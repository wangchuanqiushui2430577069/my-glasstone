# 根据 Dan W. Hanifen 在 1980 年 3 月空军理工学院论文
#《Documentation and Analysis of the WSEG-10 Fallout Prediction Model》整理的
# WSEG-10 放射性沉降模型。
#
# WSEG-10 由 Weapons Systems Evaluation Group 于 1959 年开发，目标是提供
# 一套“廉价、易用、解析式”的沉降预测代码。Hanifen 在 1980 年将其描述为
# 过去二十年中最常用的解析沉降模型之一。考虑到它在毁伤评估中的历史用途以及
# 相对简洁的实现方式，Oak Ridge National Laboratory 的 Vincent Jodoin 建议
# 将它纳入 glasstone 核武器效应库。
#
# 这里的实现旨在按照 Hanifen 1980 年 AFIT 论文中的描述，提供一个可移植的
# Python 版本。
#
# 与所有沉降模型一样，WSEG-10 并不具备真正的预测能力。它给出的是一种
# “平均情形”的合理估计：从爆点下风向热线延伸的椭圆沉降区、横风向高斯分布，
# 再叠加一个逆风修正因子。虽然设定较为简化，但并不一定比更复杂的模型更差。
# 像 DELFIC、SEER 这类更复杂的模型，只有在输入数据足够准确时，额外复杂度
# 才可能转化成更高的预测精度；而考虑到云升、粒径与活度分布等不确定性，
# 甚至未必总能做到这一点。对基础毁伤评估而言，只要清楚它的优缺点，
# WSEG-10 依然是可用的。

import numpy as np
from scipy.special import gamma
from scipy.stats import norm
from scipy.integrate import quad
from scipy.optimize import brentq
from affine import Affine
from glasstone.utilities import convert_units

def _require_finite(value, name):
    """校验输入值为有限数。"""
    if not np.isfinite(value):
        raise ValueError(f'{name} must be finite')

def _require_positive(value, name):
    """校验输入值为有限正数。"""
    if not np.isfinite(value) or value <= 0:
        raise ValueError(f'{name} must be greater than 0')

def _require_positive_array(values, name):
    """校验输入为元素全为有限正数的数组。"""
    array = np.asarray(values, dtype=float)
    if array.ndim == 0 or not np.all(np.isfinite(array)) or np.any(array <= 0):
        raise ValueError(f'{name} must be an array-like of finite values greater than 0')
    return array

def _require_fraction(value, name):
    """校验输入值位于 0 到 1 之间。"""
    if not np.isfinite(value) or not 0.0 <= value <= 1.0:
        raise ValueError(f'{name} must be between 0 and 1')

class WSEG10:
    """WSEG-10 放射性沉降解析模型。"""
    def __init__(self, gzx, gzy, yld, ff, wind, wd, shear, tob=0, dunits='km', wunits='km/h', shearunits='m/s-km', yunits='kT'):
        """根据爆点、当量、风场和裂变份额初始化沉降模型参数。"""
        gzx_mi = convert_units(gzx, dunits, 'mi')
        gzy_mi = convert_units(gzy, dunits, 'mi')
        yld_mt = convert_units(yld, yunits, 'MT')
        wind_mph = convert_units(wind, wunits, 'mph')
        shear_mph_per_kilofoot = convert_units(shear, shearunits, 'mph/kilofoot')
        _require_finite(gzx_mi, 'ground zero x')
        _require_finite(gzy_mi, 'ground zero y')
        _require_finite(wd, 'wind direction')
        _require_finite(shear_mph_per_kilofoot, 'wind shear')
        _require_finite(tob, 'time of burst')
        _require_positive(yld_mt, 'yield')
        _require_fraction(ff, 'fission fraction')
        _require_positive(wind_mph, 'wind speed')
        if tob != 0:
            raise ValueError('time of burst is currently unsupported; only tob=0 is accepted')
        self.translation = ~Affine.translation(gzx_mi, gzy_mi) # 将坐标平移为相对地爆点的位置（法定英里）
        self.wd = wd # 风向角，单位为度（0=北，90=东）
        self.yld = yld_mt # 当量（MT）
        self.ff = ff # 裂变份额，0 <= ff <= 1.0
        self.wind = wind_mph # 风速（英里/小时）
        self.shear = shear_mph_per_kilofoot # 风切变（英里/小时/千英尺）
        self.tob = tob # 为保持 API 兼容而保留；当前实现只支持 tob == 0 的情况
        # FORTRAN 风格的中间量换成什么语言都不太好看。
        # 这些值缓存到对象里，避免重复计算。
        d = np.log(self.yld) + 2.42 # 物理意义不强，但在公式中会重复出现
        # Hanifen 认为，云团最初由核火球汽化地表和武器本体形成，其中既包含
        # 中子诱导活度，也包含裂变产物。随后火球上升，外围冷却快于中心，
        # 形成典型的环形云流。WSEG 经验性地假设云团会在 15 分钟内升到最高中心
        # 高度，然后趋于稳定。
        self.H_c =  44 + 6.1 * np.log(self.yld) - 0.205 * abs(d) * d # 云团中心高度
        lnyield = np.log(self.yld)
        self.s_0 = np.exp(0.7 + lnyield / 3 - 3.25 / (4.0 + (lnyield + 5.4)**2)) # sigma_0
        self.s_02 = self.s_0**2
        self.s_h = 0.18 * self.H_c # sigma_h
        self.T_c = 1.0573203 * (12 * (self.H_c / 60) - 2.5 * (self.H_c / 60)**2) * (1 - 0.5 * np.exp(-1 * (self.H_c / 25)**2)) # 时间常数
        self.L_0 = self.wind * self.T_c # L_0，供 g(x) 使用
        self.L_02 = self.L_0**2
        self.s_x2 = self.s_02 * (self.L_02 + 8 * self.s_02) / (self.L_02 + 2 * self.s_02)
        self.s_x = np.sqrt(self.s_x2) # sigma_x
        self.L_2 = self.L_02 + 2 * self.s_x2
        self.L = np.sqrt(self.L_2) # L
        self.n = (ff * self.L_02 + self.s_x2) / (self.L_02 + 0.5 * self.s_x2) # n
        self.a_1 = 1 / (1 + ((0.001 * self.H_c * self.wind) / self.s_0)) # alpha_1

    def _world_to_cloud_transform(self):
        """返回把世界坐标转换到沉降云坐标系的仿射变换。"""
        return ~Affine.rotation(-self.wd + 270) * self.translation

    def _cloud_to_world_transform(self):
        """返回把沉降云坐标转换回世界坐标的仿射变换。"""
        return ~self._world_to_cloud_transform()

    def _cloud_frame_hplus1_components(self, rx):
        """计算沉降云坐标系下 H+1 剂量率公式的中间项。"""
        f_x = self.yld * 2e6 * self.phi(rx) * self.g(rx) * self.ff
        s_y = np.sqrt(self.s_02 + ((8 * abs(rx + 2 * self.s_x) * self.s_02) / self.L) + (2 * (self.s_x * self.T_c * self.s_h * self.shear)**2 / self.L_2) + (((rx + 2 * self.s_x) * self.L_0 * self.T_c * self.s_h * self.shear)**2 / self.L**4))
        a_2 = 1 / (1 + ((0.001 * self.H_c * self.wind) / self.s_0) * (1 - norm.cdf(2 * rx / self.wind)))
        return f_x, s_y, a_2

    def _cloud_frame_hplus1(self, rx, ry, doseunits='Roentgen'):
        """在沉降云坐标系中计算 H+1 剂量率。"""
        f_x, s_y, a_2 = self._cloud_frame_hplus1_components(rx)
        f_y = np.exp(-0.5 * (ry / (a_2 * s_y))**2) / (2.5066282746310002 * s_y)
        return convert_units(f_x * f_y, 'Roentgen', doseunits)

    def _bio_factor(self, rx):
        """返回与到达时间相关的 30 天生物剂量修正因子。"""
        t_a = self.fallouttoa(rx)
        return np.exp(-(0.287 + 0.52 * np.log(t_a / 31.6) + 0.04475 * np.log((t_a / 31.6)**2)))

    def g(self, x):
        """沉降沉积分布函数。

        放射性云团在成长和输运过程中，粒子会持续回落到地面。WSEG 假设存在一个
        g(t) 函数，用来描述任意时刻到达地面的活度比例速率，其积分 G(t) 表示
        截至该时刻已经沉降的活度比例。g(t) 与水平分布无关，但会受到初始垂直
        分布以及决定粒子下落速度的活度/粒径分布影响。这里采用的 g(t) 形式来自
        RAND 计算，并假定活动度/粒径分布由 activity_size_distribution()
        给出。由于 WSEG 把 g(t) 固定下来，不同粒径分布只能通过 T_c 随当量
        变化而得到有限补偿。
        """
        _require_finite(x, 'x coordinate')
        return np.exp(-(np.abs(x) / self.L)**self.n) / (self.L * gamma(1 + 1 / self.n))

    def phi(self, x):
        """归一化的下风向/上风向分布函数。

        为了在保持归一化的同时估算逆风向沉降，这里经验性地引入了 phi。
        """
        _require_finite(x, 'x coordinate')
        w = (self.L_0 / self.L) * (x / (self.s_x * self.a_1))
        return norm.cdf(w)

    def D_Hplus1(self, x, y, dunits='km', doseunits='Sv'):
        """返回位置 x、y 在爆后 1 小时的剂量率。

        这里包含的是最终将沉积到该位置的全部活度所对应的剂量率，而不仅仅是
        H+1 小时之前已经到达的部分。
        """
        x_mi = convert_units(x, dunits, 'mi')
        y_mi = convert_units(y, dunits, 'mi')
        _require_finite(x_mi, 'x coordinate')
        _require_finite(y_mi, 'y coordinate')
        transform = self._world_to_cloud_transform()
        rx, ry = transform * (x_mi, y_mi)
        return self._cloud_frame_hplus1(rx, ry, doseunits=doseunits)
        
    def fallouttoa(self, x):
        """返回热线方向 x 处的平均沉降到达时间，任意位置的最小值为 0.5 小时。"""
        _require_finite(x, 'x coordinate')
        t_14 = self.L_02 + 0.5 * self.s_x2
        t_15 = self.L_02 / self.L_2
        t_10 = x + 2.0 * self.s_x
        return np.sqrt(0.25 + (t_15 * t_10 * t_10 * self.T_c * self.T_c * 2.0 * self.s_x2) / t_14)

    def dose(self, x, y, dunits='km', doseunits='Sv'):
        """估算位置 x、y 从沉降到达到 30 天内的总“等效剩余剂量”（ERD）。

        结果包含 90% 的恢复系数。
        """
        x_mi = convert_units(x, dunits, 'mi')
        y_mi = convert_units(y, dunits, 'mi')
        _require_finite(x_mi, 'x coordinate')
        _require_finite(y_mi, 'y coordinate')
        transform = self._world_to_cloud_transform()
        rx, _ = transform * (x_mi, y_mi)
        # 为了描述人体受照效应，WSEG 定义了“生物剂量”，也就是 DH+1 与一个
        # 转换因子 Bio 的乘积。Bio 是一个经验函数，取决于沉降到达时间和暴露
        # 时长。模型假设所受剂量中有 10% 无法修复，另外 90% 可按 30 天时间常数
        # 修复。原始关系通过数值求解并绘制成 Dose-Time 曲线，随后先近似为
        # Bio = (t / 19)**0.33；这里采用的是进一步修正后的二阶近似：
        bio = self._bio_factor(rx)
        return self.D_Hplus1(x, y, dunits=dunits, doseunits=doseunits) * bio

    def _dose_contour_points_from_roentgen(self, target_roentgen, n, dunits):
        """以 Roentgen 为内部单位求解单条总剂量等值线点集。"""
        _require_positive(target_roentgen, 'target dose')
        if not isinstance(n, (int, np.integer)) or n < 4:
            raise ValueError('n must be an integer greater than or equal to 4')

        def centerline_dose(rx):
            return self._cloud_frame_hplus1(rx, 0.0, doseunits='Roentgen') * self._bio_factor(rx)

        scale = max(float(self.L), float(self.s_x), abs(float(self.L_0)), 1.0)
        rx_min = -scale
        rx_max = scale
        samples = None
        centerline_values = None

        for _ in range(32):
            samples = np.linspace(rx_min, rx_max, 2049)
            centerline_values = np.array([centerline_dose(rx) for rx in samples])
            positive = centerline_values >= target_roentgen
            max_idx = int(np.argmax(centerline_values))

            expand_left = centerline_values[0] >= target_roentgen or max_idx == 0
            expand_right = centerline_values[-1] >= target_roentgen or max_idx == len(centerline_values) - 1

            if np.any(positive) and not expand_left and not expand_right:
                break

            if not np.any(positive) and not expand_left and not expand_right:
                raise ValueError('no finite contour exists for the requested dose')

            if expand_left:
                rx_min *= 2.0
            if expand_right:
                rx_max *= 2.0
        else:
            raise ValueError('unable to bracket a finite contour for the requested dose')

        positive_indices = np.flatnonzero(centerline_values >= target_roentgen)
        if positive_indices.size == 0:
            raise ValueError('no finite contour exists for the requested dose')

        if np.any(np.diff(positive_indices) > 1):
            raise ValueError('multiple disjoint contour segments exist for the requested dose')

        first_idx = int(positive_indices[0])
        last_idx = int(positive_indices[-1])
        contour_eq = lambda rx: centerline_dose(rx) - target_roentgen

        if np.isclose(centerline_values[first_idx], target_roentgen):
            contour_rx_min = samples[first_idx]
        else:
            contour_rx_min = brentq(contour_eq, samples[first_idx - 1], samples[first_idx])

        if np.isclose(centerline_values[last_idx], target_roentgen):
            contour_rx_max = samples[last_idx]
        else:
            contour_rx_max = brentq(contour_eq, samples[last_idx], samples[last_idx + 1])

        upper_count = n // 2 + 1
        lower_count = n - upper_count + 2
        upper_rx = np.linspace(contour_rx_min, contour_rx_max, upper_count)
        lower_rx = np.linspace(contour_rx_max, contour_rx_min, lower_count)[1:-1]
        transform = self._cloud_to_world_transform()

        def contour_point(rx, sign):
            peak = centerline_dose(rx)
            _, s_y, a_2 = self._cloud_frame_hplus1_components(rx)
            ratio = np.clip(target_roentgen / peak, 0.0, 1.0)
            ry = sign * a_2 * s_y * np.sqrt(max(0.0, -2.0 * np.log(ratio)))
            x_mi, y_mi = transform * (rx, ry)
            return (
                convert_units(x_mi, 'mi', dunits),
                convert_units(y_mi, 'mi', dunits),
            )

        points = [contour_point(rx, 1.0) for rx in upper_rx]
        points.extend(contour_point(rx, -1.0) for rx in lower_rx)
        return np.asarray(points, dtype=float)

    def dose_contour_points(self, target_dose, n=100, dunits='km', doseunits='Sv'):
        """返回总剂量等值线上的点集。

        返回值是形状为 `(n, 2)` 的 `numpy.ndarray`，每一行对应一个 `(x, y)` 点，
        坐标单位由 `dunits` 指定，剂量单位由 `doseunits` 指定。
        """
        target_roentgen = convert_units(target_dose, doseunits, 'Roentgen')
        return self._dose_contour_points_from_roentgen(target_roentgen, n, dunits)

    def dose_contour_point_sets(self, target_doses, n=100, dunits='km', doseunits='Sv'):
        """按输入剂量数组批量返回总剂量等值线点集。

        返回值是形状为 `target_doses.shape + (n, 2)` 的 `numpy.ndarray`。也就是：
        对输入数组中的每一个剂量值，都返回一组形状为 `(n, 2)` 的轮廓点。
        """
        target_roentgen = convert_units(np.asarray(target_doses, dtype=float), doseunits, 'Roentgen')
        targets = _require_positive_array(target_roentgen, 'target doses')
        if not isinstance(n, (int, np.integer)) or n < 4:
            raise ValueError('n must be an integer greater than or equal to 4')

        contours = [
            self._dose_contour_points_from_roentgen(float(target), n, dunits)
            for target in targets.reshape(-1)
        ]
        return np.stack(contours, axis=0).reshape(targets.shape + (n, 2))
