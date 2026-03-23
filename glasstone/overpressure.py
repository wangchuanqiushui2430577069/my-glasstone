# 美苏来源的超压相关函数。

import numpy as np
from scipy.integrate import quad
from scipy.optimize import brentq
from glasstone.utilities import convert_units, ValueOutsideGraphError

# 先放一些工具函数；按理说之后应该拆到单独文件里。
def scale_range(bomb_yield, ground_range):
    """按当量立方根律缩放地面距离。"""
    return ground_range / (bomb_yield**(1.0 / 3))

def scale_height(bomb_yield, burst_height):
    """按当量立方根律缩放爆高。"""
    return burst_height / (bomb_yield**(1.0 / 3))

def _sorted_xy_pairs(xs, ys):
    """按 x 坐标升序重排一组数字化曲线点。"""
    pairs = sorted(zip(xs, ys), key=lambda pair: pair[0])
    return [x for x, _ in pairs], [y for _, y in pairs]


def _require_positive_range(range_value):
    """校验距离输入为有限正数或正数组。"""
    values = np.asarray(range_value)
    if not np.all(np.isfinite(values)) or np.any(values <= 0):
        raise ValueError('range must be greater than 0')

def _require_positive_yield(yield_value):
    """校验当量输入为有限正数或正数组。"""
    values = np.asarray(yield_value)
    if not np.all(np.isfinite(values)) or np.any(values <= 0):
        raise ValueError('yield must be greater than 0')

def _require_positive_scalar(value, name):
    """校验输入为有限正标量。"""
    values = np.asarray(value)
    if values.ndim != 0 or not np.isfinite(values).all():
        raise ValueError(f'{name} must be a finite scalar greater than 0')
    scalar = float(values)
    if scalar <= 0:
        raise ValueError(f'{name} must be greater than 0')
    return scalar

def _require_nonnegative_scalar(value, name):
    """校验输入为有限非负标量。"""
    values = np.asarray(value)
    if values.ndim != 0 or not np.isfinite(values).all():
        raise ValueError(f'{name} must be a finite scalar greater than or equal to 0')
    scalar = float(values)
    if scalar < 0:
        raise ValueError(f'{name} must be greater than or equal to 0')
    return scalar

def _require_positive_array(values, name):
    """校验输入为元素全为有限正数的数组。"""
    array = np.asarray(values, dtype=float)
    if array.ndim == 0 or not np.all(np.isfinite(array)) or np.any(array <= 0):
        raise ValueError(f'{name} must be an array-like of finite values greater than 0')
    return array

def _is_anchor_height(scale_height, anchor):
    """判断缩放爆高是否落在图表锚点高度上。"""
    return bool(np.isclose(scale_height, anchor, rtol=0.0, atol=1e-12))

def _require_sample_count(n):
    """校验采样点数量参数满足求根所需的最小值。"""
    if isinstance(n, bool) or not isinstance(n, (int, np.integer)) or n < 4:
        raise ValueError('n must be an integer greater than or equal to 4')
    return int(n)

def _collect_positive_roots(residual, left, right, f_left, f_right, depth, brackets, exact_roots):
    """递归细分区间，收集正根对应的夹逼区间或精确采样点。"""
    if not (np.isfinite(f_left) and np.isfinite(f_right)):
        return

    if np.isclose(f_left, 0.0, rtol=1e-12, atol=1e-12):
        exact_roots.append(left)
        return
    if np.isclose(f_right, 0.0, rtol=1e-12, atol=1e-12):
        exact_roots.append(right)
        return
    if f_left * f_right < 0:
        brackets.append((left, right))
        return
    if depth <= 0:
        return

    midpoint = np.sqrt(left * right)
    if midpoint <= left or midpoint >= right:
        return

    f_mid = residual(midpoint)
    if not np.isfinite(f_mid):
        return

    if np.isclose(f_mid, 0.0, rtol=1e-12, atol=1e-12):
        exact_roots.append(midpoint)
        return

    if np.sign(f_mid) != np.sign(f_left) or np.sign(f_mid) != np.sign(f_right):
        _collect_positive_roots(residual, left, midpoint, f_left, f_mid, depth - 1, brackets, exact_roots)
        _collect_positive_roots(residual, midpoint, right, f_mid, f_right, depth - 1, brackets, exact_roots)

def _distance_roots_for_target(residual, target_value, rmin, rmax, n):
    """在给定距离范围内搜索并求解目标值对应的全部正根。"""
    sample_count = max(_require_sample_count(n) * 4, 200)
    samples = np.geomspace(rmin, rmax, sample_count)
    values = np.array([residual(float(sample)) for sample in samples], dtype=float)

    brackets = []
    exact_roots = []
    for idx in range(len(samples) - 1):
        _collect_positive_roots(
            residual,
            samples[idx],
            samples[idx + 1],
            values[idx],
            values[idx + 1],
            depth=3,
            brackets=brackets,
            exact_roots=exact_roots,
        )

    roots = list(exact_roots)
    roots.extend(brentq(residual, left, right) for left, right in brackets)
    unique_roots = []
    for root in sorted(float(root) for root in roots):
        if not unique_roots or not np.isclose(root, unique_roots[-1], rtol=1e-8, atol=0.0):
            unique_roots.append(root)

    if not unique_roots:
        raise ValueOutsideGraphError(target_value)

    return np.array(unique_roots)

# 超压函数来自 H.L. Brode《Airblast From Nuclear Bursts: Analytic Approximations》
#（Pacific-Sierra Research Corporation，1986）第 60-71 页。
# 该式似乎基于理想地表冲击波模型数据的傅里叶变换拟合而来，其中许多局部函数
# 本身并没有普适的物理意义。

def _brode(z, r, y):
    """Brode 方程，用于近似 1 kT 爆炸的峰值静超压。

    单位：当量为 kT，地面距离和爆高为千英尺。
    注意：精度大约在 10% 左右，并默认海平面环境气压。
    """
    def a(z):
        return 1.22 - ((3.908 * z**2) / (1 + 810.2 * z**5))
    def b(z):
        return 2.321 + ((6.195 * z**18) / (1 + 1.113 * z**18)) - ((0.03831 * z**17) / (1 + 0.02415 * z**17)) + (0.6692 / (1 + 4164 * z**8))
    def c(z):
        return 4.153 - ((1.149 *  z**18) / (1 + 1.641 * z **18)) - (1.1 / (1 + 2.771 * z**2.5))
    def d(z):
        return -4.166 + ((25.76 * z**1.75) / (1 + 1.382 * z**18)) + ((8.257 * z) / (1 + 3.219 * z))
    def e(z):
        return 1 - ((0.004642 * z**18) / (1 + 0.003886 * z**18))
    def f(z):
        return 0.6096 + ((2.879 * z**9.25) / (1 + 2.359 * z**14.5)) - ((17.5 * z**2) / (1 + 71.66 * z**3))
    def g(z):
        return 1.83 + ((5.361 * z**2) / (1 + 0.3139 * z**6))
    def h(z, r, y):
        return ((8.808 * z**1.5) / (1 + 154.5 * z**3.5)) - ((0.2905 + 64.67 * z**5) / (1 + 441.5 * z**5)) - ((1.389 * z) / (1 + 49.03 * z**5)) + ((1.094 * r**2) / ((781.2 - (123.4 * r) + (37.98 * r**1.5) + r**2) * (1 + (2 * y))))
    def j(y):
        return ((0.000629 * y**4) / (3.493e-9 + y**4)) - ((2.67 * y**2) / (1 + (1e7 * y**4.3)))
    def k(y):
        return 5.18 + ((0.2803 * y**3.5) / (3.788e-6 + y**4))
    return (10.47 / r**a(z)) + (b(z) / r**c(z)) + ((d(z) * e(z)) / (1 + (f(z) * r**g(z)))) + h(z, r, y) + (j(y) / r**k(y))

def _brodeop(bomb_yield, ground_range, burst_height):
    """使用 Brode 方程计算任意爆高空爆的超压。

    单位：kT、千英尺。
    注意：`ground_range = 0` 会导致除零错误。
    """
    z = (burst_height / ground_range)
    y = scale_height(bomb_yield, burst_height)
    x = scale_range(bomb_yield, ground_range)
    r = (x**2 + y**2)**0.5
    return _brode(z, r, y)

def brode_overpressure(y, r, h, yunits='kT', dunits='m', opunits='kg/cm^2'):
    """使用 Brode 方程估算当量 y、爆高 h 的爆炸在半径 r 处的峰值静超压。"""
    yld = convert_units(y, yunits, 'kT')
    _require_positive_yield(yld)
    ground_range = convert_units(r, dunits, 'kilofeet')
    _require_positive_range(ground_range)
    height = _require_nonnegative_scalar(convert_units(h, dunits, 'kilofeet'), 'height')
    op = _brodeop(yld, ground_range, height)
    return convert_units(op, 'psi', opunits)

def _brode_distance_for_target(target, yld, height, n):
    """对单个目标超压执行 Brode 反算，并返回最近距离解。"""
    residual = lambda ground_range: _brodeop(yld, ground_range, height) - target
    roots = _distance_roots_for_target(
        residual,
        target,
        convert_units(1e-6, 'm', 'kilofeet'),
        convert_units(1e9, 'm', 'kilofeet'),
        n,
    )
    return float(roots[0])

def brode_overpressure_points(target_op, y, h, n=100, yunits='kT', dunits='m', opunits='kg/cm^2'):
    """反算满足目标静超压的爆心距离。

    虽然函数名沿用了先前“points”的提法，这里返回的是单个距离值。若 Brode
    模型在低超压区出现多个解，则返回离爆点最近的那个距离。
    """
    target = convert_units(_require_positive_scalar(target_op, 'target_op'), opunits, 'psi')
    yld = convert_units(y, yunits, 'kT')
    _require_positive_yield(yld)
    height = _require_nonnegative_scalar(convert_units(h, dunits, 'kilofeet'), 'height')
    return float(convert_units(_brode_distance_for_target(target, yld, height, n), 'kilofeet', dunits))

def brode_overpressure_distances(target_ops, y, h, n=100, yunits='kT', dunits='m', opunits='kg/cm^2'):
    """按输入超压数组逐个反算爆心距离，并返回同形状的距离数组。

    每个超压值都复用 `brode_overpressure_points` 的判定逻辑；若某个超压值存在
    多个数值解，则返回离爆点最近的那个距离。
    """
    targets = convert_units(_require_positive_array(target_ops, 'target_ops'), opunits, 'psi')
    yld = convert_units(y, yunits, 'kT')
    _require_positive_yield(yld)
    height = _require_nonnegative_scalar(convert_units(h, dunits, 'kilofeet'), 'height')

    distances = [
        convert_units(_brode_distance_for_target(float(target), yld, height, n), 'kilofeet', dunits)
        for target in targets.reshape(-1)
    ]
    return np.array(distances, dtype=float).reshape(targets.shape)


# 下面这组函数经 NRDC《The U.S. Nuclear War Plan: A Time for Change》转引，
# 原始内容又来自里根时期 Defense Nuclear Agency 的 DOS 程序 BLAST 和 WE
# 的帮助文档。
#
# 空爆模型摘要：
# - 计算地表峰值静超压和峰值动压，输入为爆高、地面距离和武器当量。
# - 假定地表“近似理想”：平坦、刚性、洁净且热学上可反射，不考虑建筑、地形、
#   可变形材料、地表吸热以及尘土卷入流场等影响。
# - 模型区分常规反射区与马赫反射区，并提供马赫干形成距离、三重点高度、
#   波形、冲量和正相持续时间等计算。
# - 双峰超压波形与动压波形主要来自 Speicher、Brode 等人的拟合，并结合
#   REFLECT-4 结果做了若干修正。
#
# 精度摘要：
# - 峰值超压拟合通常约为 +-4%，最差约 11%；扩展到 350,000 kPa 时平均误差约 +-10%。
# - 动压拟合与 REFLECT-4 相比平均差异约 +-32%，若只看马赫区约 +-19%。
# - 冲量积分与约 2000 个时间点的 Simpson 积分相比，误差通常小于 1%。
#
# 主要资料：
# - Bleakney 与 Taub, "Interaction of Shock Waves", Reviews of Modern Physics, 1949.
# - Carpenter, Height-of-Burst Curves, RDA letter, 1978.
# - DNA Effects Manual Number 1. Capabilities of Nuclear Weapons, unpublished.
# - Hikida, "Triple Point Path Fit (1 kt)", letter, 1982.
# - Sachs, ed., EM-1 Air Blast Phenomena, 1981.
# - Smiley、Ruetenik、Tomayko, REFLECT-4 Code Computations..., 1982.
# - Speicher 与 Brode, Airblast Overpressure Analytic Expression..., 1981.
# - Speicher, "PSR QUICK FIX Analytic Expression for Dynamic Pressure-Time...", 1983.
#
# 其他参考资料：
# - Brode, Height-of-Burst Effects at High Overpressures, 1970.
# - Brode, Theoretical Descriptions of the Blast and Fireball for a Sea Level Megaton Explosion, 1959.
# - Brode, Analytic Approximations to Dynamic Pressure and Impulse and Other Fits for Nuclear Blasts, 1983.
# - Carpenter, Overpressure Impulse HOB Curves, 1976.
# - Speicher 与 Brode, Revised Procedure for the Analytic Approximation of Dynamic Pressure Versus Time, 1980.


# 高度缩放因子（SP、SD、ST）

def _altitude_t(alt):
    """返回指定高度下的大气温度缩放因子。"""
    if 0 <= alt < 11000:
        return 1 - (2 * 10**9)**-0.5 * alt
    if 11000 <= alt < 20000:
        return 0.7535 * (1 + (2.09 * 10**-7) * alt)
    if alt >= 20000:
        return 0.684 *  (1 + (5.16 * 10**-6) * alt)

def _altitude_p(alt):
    """返回指定高度下的大气压力缩放因子。"""
    if 0 <= alt < 11000:
        return _altitude_t(alt)**5.3
    if 11000 <= alt < 20000:
        return 1.6**0.5 * (1 + (2.09 * 10**-7) * alt)**-754 
    if alt >= 20000:
        return 1.4762 *  (1 + (5.16 * 10**-6) * alt)**-33.6

def _altitude_sp(alt):
    """返回 DNA 模型中的压力缩放因子 SP。"""
    return _altitude_p(alt)

def _altitude_sd(alt):
    """返回 DNA 模型中的距离缩放因子 SD。"""
    return _altitude_sp(alt)**(-1.0/3)

def _altitude_st(alt):
    """返回 DNA 模型中的时间缩放因子 ST。"""
    return _altitude_sd(alt) * _altitude_t(alt)**-0.5

# 与高度相关的声速。
# 经验规则：温度每比 15 摄氏度高 10 摄氏度，声速大约增加 1.8%。

def _altitude_speed_of_sound(alt):
    """返回指定高度下的近似声速。"""
    return (340.5 * _altitude_sd(alt)) / _altitude_st(alt)

# Defense Nuclear Agency 的 1 kT 自由空爆超压标准。
def _DNA1kTfreeairop(r):
    """返回 DNA 1 kT 自由空爆标准下的峰值静超压。"""
    return (3.04 * 10**11)/r**3 + (1.13 * 10**9)/r**2 + (7.9 * 10**6)/(r * (np.log(r / 445.42 + 3 * np.exp(np.sqrt(r / 445.42) / -3.0)))**0.5)

# PFREE：自由空爆峰值超压。
def _DNAfreeairpeakop(r, y, alt):
    """按当量和高度修正 1 kT 自由空爆静超压。"""
    r1 = r / (_altitude_sd(alt) * y**(1.0/3))
    return _DNA1kTfreeairop(r1) * _altitude_sp(alt)

# 为了便于在空爆函数里复用，这几组函数中的 `r` 没有按当量缩放。
def _shock_strength(op):
    """把超压转换为冲击波强度比。"""
    return op / 101325 + 1

def _shock_gamma(op):
    """返回冲击波状态下的有效比热比。"""
    xi = _shock_strength(op)
    t = 10**-12 * xi**6
    z = np.log(xi) - (0.47 * t) / (100 + t)
    return 1.402 - (3.4 * 10**-4 * z**4) / (1+ 2.22 * 10**-5 * z**6)

def _shock_mu(g):
    """根据比热比计算 Rankine-Hugoniot 参数 mu。"""
    return (g + 1) / (g - 1)

def _mass_density_ratio(op):
    """返回冲击前后空气密度比。"""
    xi = _shock_strength(op)
    mu = _shock_mu(_shock_gamma(op))
    return (1 + mu * xi) / (5.975 + xi)

def _DNA1kTfreeairdyn(r):
    """返回 DNA 1 kT 自由空爆标准下的峰值动压。"""
    op = _DNA1kTfreeairop(r)
    return 0.5 * op * (_mass_density_ratio(op) - 1)

# QFREE：自由空爆峰值动压。
def _DNAfreeairpeakdyn(r, y, alt):
    """按当量和高度修正 1 kT 自由空爆动压。"""
    r1 = r / (_altitude_sd(alt) * y**(1.0/3))
    return _DNA1kTfreeairdyn(r1) * _altitude_sp(alt)

def _scaledfreeairblastwavetoa(r):
    """返回自由空爆冲击波到达时间的缩放表达式。"""
    r2 = r * r
    return (r2 * (6.7 + r)) / (7.12e6 + 7.32e4 * r + 340.5 * r2)

def _freeairblastwavetoa(r, y, alt):
    """返回给定当量和高度下的自由空爆冲击波到达时间。"""
    return _scaledfreeairblastwavetoa(r) * _altitude_st(alt) * y**(1.0/3)

# Rankine-Hugoniot 因子。
def _normal_reflection_factor(op):
    """返回法向反射条件下的超压放大系数。"""
    g = _shock_gamma(op)
    n = _mass_density_ratio(op)
    return 2 + ((g + 1) * (n - 1)) / 2

def _peak_particle_mach_number(pfree):
    """返回自由空爆峰值粒子速度对应的马赫数。"""
    n = _mass_density_ratio(pfree)
    return ((pfree * (1 - (1 /n))) / 142000)**0.5

def _shock_front_mach_number(pfree):
    """返回冲击波波前的马赫数。"""
    n = _mass_density_ratio(pfree)
    vc = _peak_particle_mach_number(pfree)
    return vc / (1 - 1 / n)

def _scale_slant_range(r, y, alt):
    """计算缩放后的斜距。"""
    sgr = r / y**(1.0/3)
    shob = alt / y**(1.0/3)
    return np.sqrt(sgr**2 + shob**2)

def _regular_mach_merge_angle(r, y, alt):
    """返回常规反射与马赫反射的合并角近似。"""
    pfree = _DNA1kTfreeairop(_scale_slant_range(r, y, alt))
    t = 340 / pfree**0.55
    u = 1 / (7782 * pfree**0.7 + 0.9)
    return np.arctan(1 / (t + u))

def _merge_region_width(r, y, alt):
    """返回常规反射与马赫反射过渡区的角宽度。"""
    pfree = _DNA1kTfreeairop(_scale_slant_range(r, y, alt))
    t = 340 / pfree**0.55
    w = 1 / (7473 * pfree**0.5 + 6.6)
    v = 1 / (647 * pfree**0.8 + w)
    return np.arctan(1 / (t + v))

def _regular_mach_switching_parameter(r, y, alt):
    """返回在常规反射和马赫反射之间平滑切换的参数。"""
    sgr = r / y**(1.0/3)
    shob = alt / y**(1.0/3)
    alpha = np.arctan(shob / sgr)
    s = (alpha - _regular_mach_merge_angle(r, y, alt)) / _merge_region_width(r, y, alt)
    s0 = max(min (s, 1), -1)
    return 0.5 * (np.sin(0.5 * np.pi * s0) + 1)

def _p_mach(r, y, alt):
    """返回马赫反射区的峰值静超压近似。"""
    sgr = r / y**(1.0/3)
    shob = alt / y**(1.0/3)
    alpha = np.arctan(shob / sgr)
    a = min(3.7 - 0.94 * np.log(sgr), 0.7)
    b = 0.77 * np.log(sgr) - 3.8 - 18 / sgr
    c = max(a, b)
    return _DNA1kTfreeairop(sgr / 2**(1.0/3)) / (1 - c * np.sin(alpha))

def _p_reg(r, y, alt):
    """返回常规反射区的峰值静超压近似。"""
    sgr = r / y**(1.0/3)
    pfree = _DNA1kTfreeairop(_scale_slant_range(r, y, alt))
    shob = alt / y**(1.0/3)
    alpha = np.arctan(shob / sgr)
    r_n = 2 + ((_shock_gamma(pfree) + 1) * (_mass_density_ratio(pfree) - 1)) / 2
    f = pfree / 75842
    d = (f**6 * (1.2 + 0.07 * f**0.5) ) / (f**6 + 1)
    return pfree * ((r_n - 2) * np.sin(alpha)**d + 2)

# PAIR：空爆峰值静超压。
def _DNAairburstpeakop(r, y, alt):
    """返回 DNA 空爆模型的峰值静超压。"""
    sigma = _regular_mach_switching_parameter(r, y, alt)
    if sigma == 0:
        return _p_mach(r, y, alt)
    elif 0 < sigma < 1:
        return _p_reg(r, y, alt) * sigma + _p_mach(r, y, alt) * (1 - sigma)
    elif sigma == 1:
        return _p_reg(r, y, alt)

# QAIR：空爆峰值动压。
def _DNAairburstpeakdyn(r, y, alt):
    """返回 DNA 空爆模型的峰值动压。"""
    pair = _DNAairburstpeakop(r, y, alt)
    sigma = _regular_mach_switching_parameter(r, y, alt)
    sgr = r / y**(1.0/3)
    shob = alt / y**(1.0/3)
    alpha = np.arctan(shob / sgr)
    n_q = _mass_density_ratio(pair)
    return 0.5 * pair * (n_q - 1) * (1 - (sigma * np.sin(alpha)**2))

# 空爆冲击波到达时间。
def _scaledmachstemformationrange(y, alt):
    """返回马赫干开始形成时的缩放地面距离。"""
    shob = alt / y**(1.0/3)
    return shob**2.5 / 5822 + 2.09 * shob**0.75

def _slantrangescalingfactor(r, y, alt):
    """返回从地面距离到等效斜距的修正因子。"""
    sgr = r / y**(1.0/3)
    x_m = _scaledmachstemformationrange(y, alt)
    if sgr <= x_m:
        return 1
    else:
        return 1.26 - 0.26 * (x_m / sgr)

# TAAIR：空爆冲击波到达时间。
def _airburstblastwavetoa(r, y, alt):
    """返回 DNA 空爆冲击波到达时间。"""
    v = _slantrangescalingfactor(r, y, alt)
    r1 = _scale_slant_range(r, y, alt) / v
    ta_air = _scaledfreeairblastwavetoa(r1)
    return ta_air * y**(1.0/3) * v

# 超压总冲量。

def _scaledopposphasedursurf(r, y, alt):
    """返回地表条件下正相持续时间的缩放近似。"""
    v = _slantrangescalingfactor(r, y, alt)
    r1 = _scale_slant_range(r, y, alt) / v
    ta_air = _scaledfreeairblastwavetoa(r1)
    t_0 = np.log(1000 * ta_air) / 3.77
    return (155 * np.exp(-20.8 * ta_air) + np.exp(-t_0**2 + 4.86 * t_0 + 0.25)) / 1000

def _scaledopposphasedur(r, y, alt):
    """返回空爆条件下正相持续时间的缩放近似。"""
    shob = alt / y**(1.0/3)
    v = _slantrangescalingfactor(r, y, alt)
    r1 = _scale_slant_range(r, y, alt) / v
    ta_air = _scaledfreeairblastwavetoa(r1) * v
    t_0 = np.log(1000 * ta_air) / 3.77
    dp_surf = (155 * np.exp(-20.8 * ta_air) + np.exp(-t_0**2 + 4.86 * t_0 + 0.25)) / 1000
    dp_unmod = dp_surf * (1 - (1 - 1 / (1 + 4.5e-8 * shob**7)) * (0.04 + 0.61 / (1 + ta_air**1.5 / 0.027)))
    return dp_unmod * 1.16 * np.exp(-abs(shob / 0.3048 - 156) / 1062)

# 双峰超压波形相关函数。

def _peaksequalityapprox(shob):
    """近似给出双峰波形两峰相等时的缩放距离。"""
    return 138.3 / (1 + 45.5 / shob)

def _peakstimeseperationapprox(shob, sgr, x_m):
    """近似给出双峰波形两峰之间的时间间隔。"""
    return max(shob / 8.186e5 * (sgr - x_m)**1.25, 1e-12)

def _DNA_b(sgr, shob, ta_air, dp, t):
    """DNA 超压/动压计算中使用的一组内部经验函数，物理解释意义并不强。"""
    s = 1 - 1 / (1 + (1 / 4.5e-8 * shob**7)) - ((5.958e-3 * shob**2) / (1 + 3.682e-7 * shob**7)) / (1 + sgr**10 / 3.052e14)
    f = s * ((2.627 * ta_air**0.75) / (1 + 5.836 * ta_air) + (2341 * ta_air**2.5) / (1 + 2.541e6 * ta_air**4.75  - 0.216)) + 0.7076 - 3.077 / (1e-4 * ta_air**-3 + 4.367)
    g = 10 + s * (77.58 - 154 * ta_air**0.125 / (1 + 1.375 * np.sqrt(ta_air)))
    h = s * ((17.69 * ta_air) / (1 + 1803 * ta_air**4.25) - (180.5 * ta_air**1.25) / (1 + 99140 * ta_air**4) - 1.6) + 2.753 + 56 * ta_air /(1 + 1.473e6 * ta_air**5)
    return (f * (ta_air / t)**g + (1 - f) * (ta_air / t)**h) * (1 - (t - ta_air) / dp)

def _opatscaledtime(r, y, alt, sgr, shob, x_m, ta_air, dp, t):
    """返回缩放时间 t 上的瞬时静超压。"""
    s = 1 - 1 / (1 + (1 / 4.5e-8 * shob**7)) - ((5.958e-3 * shob**2) / (1 + 3.682e-7 * shob**7)) / (1 + sgr**10 / 3.052e14)
    f = s * ((2.627 * ta_air**0.75) / (1 + 5.836 * ta_air) + (2341 * ta_air**2.5) / (1 + 2.541e6 * ta_air**4.75  - 0.216)) + 0.7076 - 3.077 / (1e-4 * ta_air**-3 + 4.367)
    g = 10 + s * (77.58 - 154 * ta_air**0.125 / (1 + 1.375 * np.sqrt(ta_air)))
    h = s * ((17.69 * ta_air) / (1 + 1803 * ta_air**4.25) - (180.5 * ta_air**1.25) / (1 + 99140 * ta_air**4) - 1.6) + 2.753 + 56 * ta_air /(1 + 1.473e6 * ta_air**5)
    b = (f * (ta_air / t)**g + (1 - f) * (ta_air / t)**h) * (1 - (t - ta_air) / dp)
    if x_m > sgr or shob > 116:
        return _DNAairburstpeakop(r, y, alt) * b
    else:
        x_e = _peaksequalityapprox(shob)
        e = max(min(abs((sgr - x_m) / (x_e - sgr)), 50), 0.02)
        w = 0.583 / (1 + 2477 / shob**2)
        d = 0.23 + w + 0.27 * e + e**5 * (0.5 - w)
        a = (d - 1) * (1 - 1 / (1 + e**-20))
        dt = _peakstimeseperationapprox(shob, sgr, x_m)
        v_0 = shob**6 / (2445 * (1 + shob**6.75 / 3.9e4) * (1 + 9.23 * e**2))
        c_0 = (1.04 - 1.04 / (1 + 3.725e7 / sgr**4)) / ((a + 1) * (1 + 9.872e8 / shob**9))
        g_a = max(min((t - ta_air) / dt, 400), 0.0001)
        v = 1 + v_0 * g_a**3 / (g_a**3 + 6.13)
        c = c_0 * (1 / (g_a**-7 + 0.923 * g_a**1.5)) * (1 - ((t - ta_air) / dp)**8)
        return _DNAairburstpeakop(r, y, alt) * (1 + a) * (b * v + c)

def _overpressureatscaledtime(r, y, alt, t):
    """返回给定时刻的 DNA 空爆静超压波形值。"""
    sgr = r / y**(1.0/3)
    shob = alt / y**(1.0/3)
    x_m = _scaledmachstemformationrange(y, alt)
    v = _slantrangescalingfactor(r, y, alt)
    r1 = _scale_slant_range(r, y, alt) / v
    ta_air = _scaledfreeairblastwavetoa(r1) * v
    dp = _scaledopposphasedur(r, y, alt)
    return _opatscaledtime(r, y, alt, sgr, shob, x_m, ta_air, dp, t)

# 原始 BLAST.EXE 使用 20 点高斯-勒让德求积；这里改用 scipy.integrate.quad，
# 通过经典 FORTRAN 库 QUADPACK 执行高斯-克龙罗德求积。由于它采用自适应算法
# 来把误差压到给定容差之内，通常会比 BLAST.EXE 帮助文件描述的原始做法更精确。

# IPTOTAL：超压总冲量。
def _overpressuretotalimpulse(r, y, alt):
    """对静超压波形积分，得到总冲量。"""
    sgr = r / y**(1.0/3)
    shob = alt / y**(1.0/3)
    x_m = _scaledmachstemformationrange(y, alt)
    v = _slantrangescalingfactor(r, y, alt)
    r1 = _scale_slant_range(r, y, alt) / v
    ta_air = _scaledfreeairblastwavetoa(r1) * v
    dp = _scaledopposphasedur(r, y, alt)
    t_p = 13 * (ta_air + dp) / 14
    scaled_impulse, _ = quad(lambda t: _opatscaledtime(r, y, alt, sgr, shob, x_m, ta_air, dp, t), ta_air, ta_air + dp)
    return scaled_impulse * y**(1.0/3)

def _dpt(shob, sgr, x_m, ta_air, dp_q, pair, b, t):
    """返回 DNA 动压波形在缩放时间 t 上的内部修正项。"""
    if x_m > sgr or shob > 116:
        return pair * b
    else:
        dt = _peakstimeseperationapprox(shob, sgr, x_m)
        g_a = max(min((t - ta_air) / dt, 400), 0.0001)
        x_e = _peaksequalityapprox(shob)
        e = max(min(abs((sgr - x_m) / (x_e - sgr)), 50), 0.02)
        w = 0.583 / (1 + 2477 / shob**2)
        d = 0.23 + w + 0.27 * e + e**5 * (0.5 - w)
        a = (d - 1) * (1 - 1 / (1 + e**-20))
        v_0 = shob**6 / (2445 * (1 + shob**6.75 / 3.9e4) * (1 + 9.23 * e**2))
        c_0 = (1.04 - 1.04 / (1 + 3.725e7 / sgr**4)) / ((a + 1) * (1 + 9.872e8 / shob**9))
        v = 1 + v_0 * g_a**3 / (g_a**3 + 6.13)
        c = c_0 * (1 / (g_a**-7 + 0.923 * g_a**1.5)) * (1 - ((t- ta_air) / dp_q)**8)
        return pair * (1 + a) * (b * v + c)
        
# 动压总冲量。

def _dynamicpressureatscaledtime(r, y, alt, t):
    """返回给定时刻的 DNA 空爆动压波形值。"""
    pair = _DNAairburstpeakop(r, y, alt)
    sgr = r / y**(1.0/3)
    shob = alt / y**(1.0/3)
    x_m = _scaledmachstemformationrange(y, alt)
    v = _slantrangescalingfactor(r, y, alt)
    sr = _scale_slant_range(r, y, alt)
    ta_air = _scaledfreeairblastwavetoa(sr / v) * v
    shob_0 = shob / 0.3048
    sgr_0 = sgr / 0.3048
    shob_x = abs(shob_0 - 200) + 200
    sgr_x = sgr_0 - 200
    dp_0 = 0.3 + 0.42 * np.exp(-shob_x / 131)
    if sgr_x > 0:
        dp_x = dp_0 + 4.4e-5 * sgr_x
    else:
        dp_x = dp_0 + sgr_x * (1.0 / 2361 - abs(shob_x -533)**2 / 7.88e7)
    if shob_0 >= 200:
        dp_q = dp_x
    else:
        dp_q = dp_x * (1 + 0.2 * np.sin(shob_0 * np.pi /200))
    delta_0 = max(shob_0**1.52 / 16330 - 0.29, 0)
    delta = 2.38 * np.exp(-7e-7 * abs(shob_0 - 750)**2.7 - 4e-7 * sgr_0**2) + delta_0
    b = _DNA_b(sgr, shob, ta_air, dp_q, t)
    dpt = _dpt(shob, sgr, x_m, ta_air, dp_q, pair, b, t)
    n_q = _mass_density_ratio(dpt)
    return 0.5 * dpt * (n_q - 1) * (dpt / pair)**delta

def DNA_static_overpressure(y, r, h, yunits='kT', dunits='m', opunits='kg/cm^2'):
    """基于 Defense Nuclear Agency 的 1 kT 自由空爆标准，估算当量 y 的爆炸
    在距离 r、爆高 h 条件下的峰值静超压。

    该模型假定地表在热学上是理想表面。
    """
    yld = convert_units(y, yunits, 'kT')
    _require_positive_yield(yld)
    gr = convert_units(r, dunits, 'm')
    _require_positive_range(gr)
    height = convert_units(h, dunits, 'm')
    op = _DNAairburstpeakop(gr, yld, height)
    return convert_units(op, 'Pa', opunits)

def DNA_static_overpressure_points(target_op, y, h, n=100, yunits='kT', dunits='m', opunits='kg/cm^2'):
    """反算满足目标静超压的爆心距离。

    该接口返回单个距离值；若出现多个数值解，则返回离爆点最近的那个距离。
    """
    target = convert_units(_require_positive_scalar(target_op, 'target_op'), opunits, 'Pa')
    yld = convert_units(y, yunits, 'kT')
    _require_positive_yield(yld)
    height = convert_units(h, dunits, 'm')
    residual = lambda ground_range: _DNAairburstpeakop(ground_range, yld, height) - target
    roots = _distance_roots_for_target(residual, target, 1e-6, 1e9, n)
    return float(convert_units(roots[0], 'm', dunits))

def DNA_dynamic_pressure(y, r, h, yunits='kT', dunits='m', opunits='kg/cm^2'):
    """基于 Defense Nuclear Agency 的 1 kT 自由空爆标准，估算当量 y 的爆炸
    在距离 r、爆高 h 条件下的峰值动压。

    该模型假定地表是理想表面；而现实中的地表往往并非如此，因此它的预测能力
    只能视为有限。
    """
    yld = convert_units(y, yunits, 'kT')
    _require_positive_yield(yld)
    gr = convert_units(r, dunits, 'm')
    _require_positive_range(gr)
    height = convert_units(h, dunits, 'm')
    dyn = _DNAairburstpeakdyn(gr, yld, height)
    return convert_units(dyn, 'Pa', opunits)

# 苏联《Iadernoe oruzhie》中的超压函数。这里尽量贴近原手册，因此直接使用了
# 通过 GraphClick 从图上提取的原始数据点。之前尝试用二维样条拟合，代码虽然更
# 简洁，但会在 0、70、120、200 m 爆高曲线之间产生不符合物理直觉的深谷。
# 虽然这段实现不算漂亮，而且不少数据处理还是程序生成的，但在缩放爆高不超过
# 200 m、缩放距离不超过 5000 m 的范围内，它对苏联原图给出了比较可信的读数。

_soviet_mach_sh20x = [1.331, 19.104, 41.226, 63.169, 80.125, 101.245, 120.583, 139.581, 159.644, 179.461, 201.321, 219.973, 241.426, 262.938, 283.481, 302.758, 325.245, 344.411, 366.451, 385.868, 406.185, 421.308, 438.554, 461.104, 481.968, 502.29, 521.382, 539.832, 560.252, 581.467, 602.997, 624.007, 644.188, 664.264, 685.195, 701.97, 719.388, 742.154, 759.153, 782.1, 801.371, 821.661, 838.498, 859.418, 881.444, 900.283, 918.763, 937.479, 959.438, 981.26, 998.157, 1018.784, 1036.762, 1058.714, 1076.708, 1096.315, 1115.931, 1135.326, 1155.808, 1176.399, 1195.328, 1215.69, 1238.752, 1257.313, 1277.394, 1297.996]

_soviet_mach_sh20y = [0.5523031, 0.54294986, 0.52608067, 0.49927458, 0.46671936, 0.43120286, 0.38863397, 0.3422252, 0.29534715, 0.24551266, 0.19700474, 0.14952701, 0.10209052, 0.06145246, 0.015359761, -0.022733787, -0.06198091, -0.09582564, -0.13548893, -0.16877033, -0.20134935, -0.22329882, -0.2502637, -0.28066874, -0.3080349, -0.3362991, -0.35951856, -0.38090667, -0.4089354, -0.43179828, -0.45717457, -0.48412615, -0.5044557, -0.5228787, -0.54363394, -0.5606673, -0.5783961, -0.5968795, -0.61261016, -0.6326441, -0.64589155, -0.6635403, -0.67366415, -0.6946486, -0.71219826, -0.7235382, -0.74232143, -0.75696194, -0.7670039, -0.78515613, -0.7986029, -0.8153086, -0.8268137, -0.838632, -0.8507809, -0.86012095, -0.8728952, -0.8827287, -0.8894103, -0.89619625, -0.91009486, -0.92081875, -0.928118, -0.93554205, -0.94309515, -0.95078194]

_soviet_mach_sh12x = [0.185, 18.063, 40.565, 62.433, 81.175, 100.851, 118.577, 140.272, 158.86, 179.04, 201.837, 220.533, 240.193, 261.484, 283.718, 303.67, 324.586, 343.316, 364.135, 385.013, 406.369, 420.938, 439.419, 460.575, 481.611, 503.282, 521.93, 539.092, 560.148, 580.711, 603.393, 625.366, 645.57, 664.924, 682.531, 702.317, 721.647, 741.642, 761.445, 780.592, 801.867, 822.177, 840.157, 859.306, 880.964, 899.578, 919.86, 939.25, 959.39, 981.444, 997.316, 1018.656, 1037.42, 1059.352, 1077.797, 1096.364, 1115.968, 1135.366, 1155.98, 1177.267, 1195.852, 1215.355, 1237.331, 1258.569, 1277.98, 1299.745]

_soviet_mach_sh12y = [1.1908078, 1.1579099, 1.0961798, 1.0184091, 0.941064, 0.83569056, 0.7261565, 0.59128726, 0.4835873, 0.38453263, 0.29336256, 0.22193561, 0.15198241, 0.08457629, 0.01870051, -0.029188387, -0.07623804, -0.11690664, -0.15926675, -0.19859628, -0.23507701, -0.2588484, -0.28903687, -0.3187588, -0.35164, -0.38510278, -0.40782323, -0.4341522, -0.46344155, -0.48811665, -0.51712644, -0.53910214, -0.5622495, -0.5783961, -0.59859943, -0.61618465, -0.6326441, -0.65169513, -0.66554624, -0.6798537, -0.6968039, -0.7144427, -0.72815835, -0.7447275, -0.75696194, -0.7695511, -0.78515613, -0.7986029, -0.8096683, -0.82390875, -0.83268267, -0.84163755, -0.8507809, -0.86327946, -0.8728952, -0.8827287, -0.89279, -0.90309, -0.91009486, -0.924453, -0.928118, -0.93930215, -0.9469216, -0.95467705, -0.9665762, -0.97469413]

_soviet_mach_sh7x = [43.498, 62.016, 80.472, 102.385, 118.651, 137.123, 159.124, 178.447, 199.181, 220.241, 240.446, 261.623, 282.019, 303.128, 324.82, 343.582, 365.69, 386.883, 407.316, 419.945, 438.6, 459.636, 482.27, 504.212, 521.759, 541.152, 558.706, 579.852, 604.245, 624.521, 644.971, 665.257, 683.297, 702.656, 721.246, 740.825, 758.882, 780.978, 801.944, 821.375, 840.749, 860.018, 882.071, 901.053, 919.378, 938.86, 960.238, 981.877, 997.851, 1019.741, 1038.082, 1057.774, 1079.01, 1096.82, 1116.995, 1135.905, 1156.171, 1178.157, 1197.581, 1216.761, 1237.352, 1259.419, 1277.991]

_soviet_mach_sh7y = [1.4558951, 1.3268068, 1.1763808, 0.9898501, 0.86093664, 0.72525805, 0.58782315, 0.46419135, 0.35755375, 0.24944296, 0.1547282, 0.08884456, 0.026124531, -0.03526908, -0.09691001, -0.13489604, -0.1811146, -0.22257319, -0.2620127, -0.28819278, -0.32239303, -0.3555614, -0.3882767, -0.4156688, -0.44129145, -0.46597388, -0.49214414, -0.5243288, -0.55284196, -0.5718652, -0.5968795, -0.61618465, -0.634512, -0.64975196, -0.66756153, -0.6819367, -0.6968039, -0.71669877, -0.73282826, -0.75202674, -0.7670039, -0.7798919, -0.79317415, -0.80410033, -0.8153086, -0.8268137, -0.84163755, -0.85387194, -0.86327946, -0.8696662, -0.87942606, -0.89279, -0.90309, -0.91364014, -0.92081875, -0.93181413, -0.94309515, -0.95078194, -0.9586073, -0.9706162, -0.9788107, -0.98716277, -0.9956786]

_soviet_nomach_sh20x = [0.776, 17.542, 39.194, 59.919, 80.395, 98.234, 120.384, 140.066, 160.045, 180.514, 200.089, 220.68, 241.361, 262.881, 282.052, 302.272, 319.874, 342.36, 361.619, 380.321, 401.146, 418.947, 440.827, 460.902, 480.084, 501.124, 520.191, 541.805, 558.649, 581.106, 600.363, 618.769, 640.37, 660.45, 680.663, 699.943, 717.806, 739.608, 759.72, 778.782, 799.109, 819.068, 838.982, 859.111, 878.461, 898.427, 918.219, 936.807, 958.894, 978.923, 997.298, 1017.674, 1037.8, 1056.481, 1077.19, 1098.258, 1118.988, 1137.451, 1158.16, 1178.221, 1200.057, 1220.432, 1238.064, 1259.942, 1280.013, 1298.179]

_soviet_nomach_sh20y = [0.47348696, 0.47085133, 0.44916973, 0.40636984, 0.33183205, 0.23426414, 0.12319806, 0.04257553, -0.026410384, -0.08777796, -0.13018179, -0.17783193, -0.21467015, -0.25103715, -0.27736607, -0.30451834, -0.32790214, -0.35066512, -0.36653155, -0.38721615, -0.40560743, -0.41793662, -0.4353339, -0.44249278, -0.45345733, -0.4596705, -0.47366074, -0.4867824, -0.49894074, -0.5128616, -0.5287083, -0.54363394, -0.5590909, -0.57675415, -0.59176004, -0.6055483, -0.6216021, -0.63827217, -0.65169513, -0.66554624, -0.6798537, -0.68824613, -0.7011469, -0.7144427, -0.73048705, -0.7375489, -0.75448734, -0.7619539, -0.7798919, -0.79048496, -0.7986029, -0.8096683, -0.82390875, -0.82973826, -0.838632, -0.8507809, -0.86012095, -0.8696662, -0.87614834, -0.88605666, -0.89619625, -0.90309, -0.91364014, -0.91721463, -0.924453, -0.93181413]

_soviet_nomach_sh12x = [0.631, 17.577, 39.951, 28.472, 58.118, 78.658, 97.327, 119.925, 139.845, 160.758, 180.155, 201.174, 220.079, 240.022, 262.906, 282.311, 300.743, 320.333, 341.52, 361.346, 381.298, 400.317, 418.635, 439.661, 459.697, 479.828, 501.458, 520.715, 541.523, 559.59, 581.485, 600.797, 619.104, 639.911, 660.035, 679.174, 698.348, 720.035, 740.337, 760.349, 779.427, 800.302, 818.658, 839.435, 859.027, 878.489, 899.365, 917.382, 938.439, 957.56, 977.898, 997.32, 1017.29, 1038.069, 1058.037, 1078.744, 1099.227, 1118.674, 1138.738, 1157.212, 1178.332, 1200.738, 1219.925, 1239.044, 1259.221, 1279.509, 1298.87]

_soviet_nomach_sh12y = [1.1146777, 1.0617163, 0.89641595, 0.9935685, 0.7189167, 0.5682017, 0.4556061, 0.35506824, 0.27669153, 0.20330492, 0.14113607, 0.08314413, 0.024895966, -0.024108874, -0.07520399, -0.114073664, -0.1518109, -0.19246496, -0.23358716, -0.2700257, -0.3053948, -0.33724216, -0.3635121, -0.38933983, -0.41453928, -0.43889862, -0.46344155, -0.47886193, -0.49894074, -0.52143353, -0.537602, -0.5590909, -0.57511836, -0.59516627, -0.6090649, -0.627088, -0.6401645, -0.653647, -0.66958624, -0.68402964, -0.6968039, -0.7099654, -0.71896666, -0.73048705, -0.74714696, -0.7619539, -0.77469075, -0.78515613, -0.8013429, -0.8068754, -0.82102305, -0.8268137, -0.83564717, -0.8477116, -0.85387194, -0.86012095, -0.87614834, -0.87942606, -0.88605666, -0.89619625, -0.90309, -0.91009486, -0.92081875, -0.928118, -0.93554205, -0.94309515, -0.9469216]

_soviet_nomach_sh7x = [39.016, 49.999, 58.378, 67.937, 79.137, 98.665, 107.882, 119.779, 141.147, 153.237, 180.792, 201.757, 220.338, 241.914, 262.073, 282.716, 303.068, 318.817, 339.29, 359.974, 381.646, 399.743, 420.145, 440.688, 460.453, 480.501, 501.541, 519.266, 541.776, 559.521, 581.203, 601.3, 618.193, 639.057, 660.737, 678.284, 699.991, 719.077, 740.641, 759.83, 778.266, 799.218, 820.055, 839.256, 858.756, 878.976, 899.119, 917.695, 936.99, 958.196, 978.491, 997.668, 1017.739, 1036.856, 1057.669, 1077.738, 1098.06, 1118.041, 1138.872, 1156.899, 1179.739, 1199.16, 1220.081, 1239.626, 1260.337, 1281.059, 1299.434]

_soviet_nomach_sh7y = [1.4701016, 1.3752978, 1.2849042, 1.0660276, 0.8939836, 0.64157325, 0.5370631, 0.43743342, 0.30297995, 0.24004978, 0.11193429, 0.044539776, -0.015922973, -0.068542145, -0.11804502, -0.15926675, -0.19722629, -0.22767828, -0.26440108, -0.3001623, -0.33161408, -0.36151075, -0.3936186, -0.4213608, -0.45345733, -0.47495517, -0.49349496, -0.5142786, -0.5346171, -0.55284196, -0.5702477, -0.5867002, -0.6055483, -0.61978877, -0.63827217, -0.6556077, -0.66958624, -0.6798537, -0.69897, -0.71219826, -0.7235382, -0.7399286, -0.75696194, -0.7670039, -0.77728355, -0.79048496, -0.80410033, -0.8096683, -0.82102305, -0.83268267, -0.838632, -0.8507809, -0.86012095, -0.86646104, -0.8728952, -0.8827287, -0.89619625, -0.90309, -0.9065783, -0.91721463, -0.924453, -0.93181413, -0.93930215, -0.94309515, -0.95078194, -0.9586073, -0.9665762]

_soviet_groundx = [66.257, 68.349, 72.247, 72.145, 74.648, 78.78, 82.873, 90.652, 98.896, 100.0, 130.0, 205.295, 306.881, 407.898, 507.448, 608.431, 704.076, 807.753, 907.192, 1007.669, 1109.542, 1202.889, 1308.551, 1405.661, 1504.885, 1599.907, 1704.178, 1800.224, 1899.995, 1997.408, 2095.903, 2193.049, 2273.675, 2420.979, 2818.851, 3207.894, 3609.952, 3992.678, 4402.044, 4807.183, 5205.226]

_soviet_groundy = [2.0277002, 1.9637926, 1.9058229, 1.847085, 1.7720796, 1.6970462, 1.5995556, 1.4737351, 1.312135, 1.0265741, 0.7101174, 0.31069332, -0.057991948, -0.29929632, -0.4609239, -0.5850267, -0.692504, -0.7798919, -0.8569852, -0.924453, -0.9788107, -1.031517, -1.0655016, -1.1023729, -1.1487416, -1.1739252, -1.2146702, -1.2441251, -1.2757242, -1.2924298, -1.3187587, -1.3467875, -1.3565474, -1.3872161, -1.4685211, -1.5528419, -1.6197888, -1.6777807, -1.7212464, -1.769551, -1.79588]

_soviet_nomach_sh12x, _soviet_nomach_sh12y = _sorted_xy_pairs(_soviet_nomach_sh12x, _soviet_nomach_sh12y)
_soviet_groundx, _soviet_groundy = _sorted_xy_pairs(_soviet_groundx, _soviet_groundy)

# 这些局部函数沿原始图线做插值；如果请求值超出苏联资料的范围，就抛出
# `ValueOutsideGraphError`。

def _soviet_mach_sh20(range):
    """沿苏联马赫反射 200 m 缩放爆高曲线插值超压。"""
    if _soviet_mach_sh20x[0] <= range <= _soviet_mach_sh20x[-1]:
        return np.interp(range, _soviet_mach_sh20x, _soviet_mach_sh20y)
    else:
        raise ValueOutsideGraphError(range)

def _soviet_mach_sh12(range):
    """沿苏联马赫反射 120 m 缩放爆高曲线插值超压。"""
    if _soviet_mach_sh12x[0] <= range <= _soviet_mach_sh12x[-1]:
        return np.interp(range, _soviet_mach_sh12x, _soviet_mach_sh12y)
    else:
        raise ValueOutsideGraphError(range)

def _soviet_mach_sh7(range):
    """沿苏联马赫反射 70 m 缩放爆高曲线插值超压。"""
    if _soviet_mach_sh7x[0] <= range <= _soviet_mach_sh7x[-1]:
        return np.interp(range, _soviet_mach_sh7x, _soviet_mach_sh7y)
    else:
        raise ValueOutsideGraphError(range)

def _soviet_nomach_sh20(range):
    """沿苏联无马赫干 200 m 缩放爆高曲线插值超压。"""
    if _soviet_nomach_sh20x[0] <= range <= _soviet_nomach_sh20x[-1]:
        return np.interp(range, _soviet_nomach_sh20x, _soviet_nomach_sh20y)
    else:
        raise ValueOutsideGraphError(range)

def _soviet_nomach_sh12(range):
    """沿苏联无马赫干 120 m 缩放爆高曲线插值超压。"""
    if _soviet_nomach_sh12x[0] <= range <= _soviet_nomach_sh12x[-1]:
        return np.interp(range, _soviet_nomach_sh12x, _soviet_nomach_sh12y)
    else:
        raise ValueOutsideGraphError(range)

def _soviet_nomach_sh7(range):
    """沿苏联无马赫干 70 m 缩放爆高曲线插值超压。"""
    if _soviet_nomach_sh7x[0] <= range <= _soviet_nomach_sh7x[-1]:
        return np.interp(range, _soviet_nomach_sh7x, _soviet_nomach_sh7y)
    else:
        raise ValueOutsideGraphError(range)

def _soviet_ground(range):
    """沿苏联地爆曲线插值超压。"""
    if _soviet_groundx[0] <= range <= _soviet_groundx[-1]:
        return np.interp(range, _soviet_groundx, _soviet_groundy)
    else:
        raise ValueOutsideGraphError(range)

def lerp10(h, h1, h2, o1, o2):
    """在线性插值得到 `o` 后返回 `10**o`，其中 `o` 对应 h 在 (h1, o1) 与
    (h2, o2) 之间的位置。"""
    return 10**np.interp(h, [h1, h2], [o1, o2])

def _sovietmach(scale_height, ground_range):
    """按苏联图表插值存在马赫干时的峰值超压。"""
    if _is_anchor_height(scale_height, 200.0):
        return 10**_soviet_mach_sh20(ground_range)
    elif _is_anchor_height(scale_height, 120.0):
        return 10**_soviet_mach_sh12(ground_range)
    elif _is_anchor_height(scale_height, 70.0):
        return 10**_soviet_mach_sh7(ground_range)
    elif _is_anchor_height(scale_height, 0.0):
        return 10**_soviet_ground(ground_range)
    elif 120 < scale_height < 200:
        return lerp10(scale_height, 120, 200, _soviet_mach_sh12(ground_range), _soviet_mach_sh20(ground_range))
    elif 70 < scale_height < 120:
        return lerp10(scale_height, 70, 120, _soviet_mach_sh7(ground_range), _soviet_mach_sh12(ground_range))
    elif 0 < scale_height < 70:
        return lerp10(scale_height, 0, 70, _soviet_ground(ground_range), _soviet_mach_sh7(ground_range))
    else:
        raise ValueOutsideGraphError(scale_height)
        
def _sovietnomach(scale_height, ground_range):
    """按苏联图表插值热层抑制马赫干时的峰值超压。"""
    if _is_anchor_height(scale_height, 200.0):
        return 10**_soviet_nomach_sh20(ground_range)
    elif _is_anchor_height(scale_height, 120.0):
        return 10**_soviet_nomach_sh12(ground_range)
    elif _is_anchor_height(scale_height, 70.0):
        return 10**_soviet_nomach_sh7(ground_range)
    elif _is_anchor_height(scale_height, 0.0):
        return 10**_soviet_ground(ground_range)
    elif 120 < scale_height < 200:
        return lerp10(scale_height, 120, 200, _soviet_nomach_sh12(ground_range), _soviet_nomach_sh20(ground_range))
    elif 70 < scale_height < 120:
        return lerp10(scale_height, 70, 120, _soviet_nomach_sh7(ground_range), _soviet_nomach_sh12(ground_range))
    elif 0 < scale_height < 70:
        return lerp10(scale_height, 0, 70, _soviet_ground(ground_range), _soviet_nomach_sh7(ground_range))
    else:
        raise ValueOutsideGraphError(scale_height)

_rsoviet_mach_sh20x = _soviet_mach_sh20x[::-1]

_rsoviet_mach_sh20y = _soviet_mach_sh20y[::-1]

_rsoviet_mach_sh12x = _soviet_mach_sh12x[::-1]

_rsoviet_mach_sh12y = _soviet_mach_sh12y[::-1]

_rsoviet_mach_sh7x = _soviet_mach_sh7x[::-1]

_rsoviet_mach_sh7y = _soviet_mach_sh7y[::-1]

_rsoviet_nomach_sh20x = _soviet_nomach_sh20x[::-1]

_rsoviet_nomach_sh20y = _soviet_nomach_sh20y[::-1]

_rsoviet_nomach_sh12x = _soviet_nomach_sh12x[::-1]

_rsoviet_nomach_sh12y = _soviet_nomach_sh12y[::-1]

_rsoviet_nomach_sh7x = _soviet_nomach_sh7x[::-1]

_rsoviet_nomach_sh7y = _soviet_nomach_sh7y[::-1]

_rsoviet_groundx = _soviet_groundx[::-1]

_rsoviet_groundy = _soviet_groundy[::-1]


def _soviet_range_bounds(scale_height, thermal_layer):
    """返回给定缩放爆高下图表可用的缩放距离区间。"""
    if thermal_layer:
        if _is_anchor_height(scale_height, 200.0):
            lower = _soviet_nomach_sh20x[0]
            upper = _soviet_nomach_sh20x[-1]
        elif _is_anchor_height(scale_height, 120.0):
            lower = _soviet_nomach_sh12x[0]
            upper = _soviet_nomach_sh12x[-1]
        elif _is_anchor_height(scale_height, 70.0):
            lower = _soviet_nomach_sh7x[0]
            upper = _soviet_nomach_sh7x[-1]
        elif _is_anchor_height(scale_height, 0.0):
            lower = _soviet_groundx[0]
            upper = _soviet_groundx[-1]
        elif 120 < scale_height < 200:
            lower = max(_soviet_nomach_sh12x[0], _soviet_nomach_sh20x[0])
            upper = min(_soviet_nomach_sh12x[-1], _soviet_nomach_sh20x[-1])
        elif 70 < scale_height < 120:
            lower = max(_soviet_nomach_sh7x[0], _soviet_nomach_sh12x[0])
            upper = min(_soviet_nomach_sh7x[-1], _soviet_nomach_sh12x[-1])
        elif 0 < scale_height < 70:
            lower = max(_soviet_groundx[0], _soviet_nomach_sh7x[0])
            upper = min(_soviet_groundx[-1], _soviet_nomach_sh7x[-1])
        else:
            raise ValueOutsideGraphError(scale_height)
    else:
        if _is_anchor_height(scale_height, 200.0):
            lower = _soviet_mach_sh20x[0]
            upper = _soviet_mach_sh20x[-1]
        elif _is_anchor_height(scale_height, 120.0):
            lower = _soviet_mach_sh12x[0]
            upper = _soviet_mach_sh12x[-1]
        elif _is_anchor_height(scale_height, 70.0):
            lower = _soviet_mach_sh7x[0]
            upper = _soviet_mach_sh7x[-1]
        elif _is_anchor_height(scale_height, 0.0):
            lower = _soviet_groundx[0]
            upper = _soviet_groundx[-1]
        elif 120 < scale_height < 200:
            lower = max(_soviet_mach_sh12x[0], _soviet_mach_sh20x[0])
            upper = min(_soviet_mach_sh12x[-1], _soviet_mach_sh20x[-1])
        elif 70 < scale_height < 120:
            lower = max(_soviet_mach_sh7x[0], _soviet_mach_sh12x[0])
            upper = min(_soviet_mach_sh7x[-1], _soviet_mach_sh12x[-1])
        elif 0 < scale_height < 70:
            lower = max(_soviet_groundx[0], _soviet_mach_sh7x[0])
            upper = min(_soviet_groundx[-1], _soviet_mach_sh7x[-1])
        else:
            raise ValueOutsideGraphError(scale_height)
    return lower, upper

def _rsoviet_mach_sh20(overpressure):
    """沿苏联马赫反射 200 m 曲线反算缩放距离。"""
    if _rsoviet_mach_sh20y[0] <= overpressure <= _rsoviet_mach_sh20y[-1]:
        return np.interp(overpressure, _rsoviet_mach_sh20y, _rsoviet_mach_sh20x)
    else:
        raise ValueOutsideGraphError(overpressure)

def _rsoviet_mach_sh12(overpressure):
    """沿苏联马赫反射 120 m 曲线反算缩放距离。"""
    if _rsoviet_mach_sh12y[0] <= overpressure <= _rsoviet_mach_sh12y[-1]:
        return np.interp(overpressure, _rsoviet_mach_sh12y, _rsoviet_mach_sh12x)
    else:
        raise ValueOutsideGraphError(overpressure)

def _rsoviet_mach_sh7(overpressure):
    """沿苏联马赫反射 70 m 曲线反算缩放距离。"""
    if _rsoviet_mach_sh7y[0] <= overpressure <= _rsoviet_mach_sh7y[-1]:
        return np.interp(overpressure, _rsoviet_mach_sh7y, _rsoviet_mach_sh7x)
    else:
        raise ValueOutsideGraphError(overpressure)

def _rsoviet_nomach_sh20(overpressure):
    """沿苏联无马赫干 200 m 曲线反算缩放距离。"""
    if _rsoviet_nomach_sh20y[0] <= overpressure <= _rsoviet_nomach_sh20y[-1]:
        return np.interp(overpressure, _rsoviet_nomach_sh20y, _rsoviet_nomach_sh20x)
    else:
        raise ValueOutsideGraphError(overpressure)

def _rsoviet_nomach_sh12(overpressure):
    """沿苏联无马赫干 120 m 曲线反算缩放距离。"""
    if _rsoviet_nomach_sh12y[0] <= overpressure <= _rsoviet_nomach_sh12y[-1]:
        return np.interp(overpressure, _rsoviet_nomach_sh12y, _rsoviet_nomach_sh12x)
    else:
        raise ValueOutsideGraphError(overpressure)

def _rsoviet_nomach_sh7(overpressure):
    """沿苏联无马赫干 70 m 曲线反算缩放距离。"""
    if _rsoviet_nomach_sh7y[0] <= overpressure <= _rsoviet_nomach_sh7y[-1]:
        return np.interp(overpressure, _rsoviet_nomach_sh7y, _rsoviet_nomach_sh7x)
    else:
        raise ValueOutsideGraphError(overpressure)

def _rsoviet_ground(overpressure):
    """沿苏联地爆曲线反算缩放距离。"""
    if _rsoviet_groundy[0] <= overpressure <= _rsoviet_groundy[-1]:
        return np.interp(overpressure, _rsoviet_groundy, _rsoviet_groundx)
    else:
        raise ValueOutsideGraphError(overpressure)

# 这算是个折中做法，但当缩放爆高大于 120 且超压高于原图 `scale_height == 200`
# 曲线的最大值时，只有这样才能得到合理结果。
def _rsovietnomach(scale_height, overpressure):
    """按苏联无马赫干图表反算缩放距离。"""
    logop = np.log10(overpressure)
    if scale_height >= 120 and overpressure > 2.975:
        l = lambda x: np.log10(_sovietnomach(scale_height, x))
        distances = [x*10 for x in range(11)][::-1]
        return np.interp(logop, [l(distance) for distance in distances], distances)
    elif 120 <= scale_height <= 200:
        return np.interp(scale_height, [120, 200], [_rsoviet_nomach_sh12(logop), _rsoviet_nomach_sh20(logop)])
    elif 70 <= scale_height < 120:
        return np.interp(scale_height, [70, 120], [_rsoviet_nomach_sh7(logop), _rsoviet_nomach_sh12(logop)])
    elif 0 <= scale_height < 70:
        return np.interp(scale_height, [0, 70], [_rsoviet_ground(logop), _rsoviet_nomach_sh7(logop)])
    else:
        raise ValueOutsideGraphError(scale_height)

def _rsovietmach(scale_height, overpressure):
    """按苏联马赫反射图表反算缩放距离。"""
    logop = np.log10(overpressure)
    if scale_height >= 120 and overpressure > 2.2336:
        l = lambda x: np.log10(_sovietmach(scale_height, x))
        distances = [x*10 for x in range(18)][::-1]
        return np.interp(logop, [l(distance) for distance in distances], distances)
    elif 120 <= scale_height <= 200:
        return np.interp(scale_height, [120, 200], [_rsoviet_mach_sh12(logop), _rsoviet_mach_sh20(logop)])
    elif 70 <= scale_height < 120:
        return np.interp(scale_height, [70, 120], [_rsoviet_mach_sh7(logop), _rsoviet_mach_sh12(logop)])
    elif 0 <= scale_height < 70:
        return np.interp(scale_height, [0, 70], [_rsoviet_ground(logop), _rsoviet_mach_sh7(logop)])
    else:
        raise ValueOutsideGraphError(scale_height)

def soviet_overpressure(y, r, h, thermal_layer=True, yunits='kT', dunits='m', opunits='kg/cm^2'):
    """根据 1987 年苏联军用资料《Iadernoe oruzhie: Posbie dlia ofitserov》中的
    图表，估算离爆心半径 r 处的峰值静超压。

    这个模型最有特点的地方在于它考虑了热层抑制马赫干的情形。大多数美国核武器
    效应研究者把这视为偏理论化的“二阶效应”，但苏联在大气核试验中观察到过
    更极端的现象，并据此认为它会在不少真实作战场景中出现。若要使用存在马赫干
    的苏联模型，请把 `thermal_layer` 设为 `False`。
    """
    yld = convert_units(y, yunits, 'kT')
    _require_positive_yield(yld)
    gr = convert_units(r, dunits, 'm')
    height = convert_units(h, dunits, 'm')
    sr = scale_range(yld, gr)
    sh = scale_height(yld, height)
    if thermal_layer:
        return convert_units(_sovietnomach(sh, sr), 'kg/cm^2', opunits)
    else:
        return convert_units(_sovietmach(sh, sr), 'kg/cm^2', opunits)

def r_soviet_overpressure(y, op, h, thermal_layer=True, yunits='kT', dunits='m', opunits='kg/cm^2'):
    """根据 1987 年苏联军用资料《Iadernoe oruzhie: Posbie dlia ofitserov》中的
    图表，反算峰值静超压为 op 时对应的爆心距离。

    这个模型同样考虑了热层抑制马赫干的情形。若要使用存在马赫干的苏联模型，
    请把 `thermal_layer` 设为 `False`。
    """
    yld = convert_units(y, yunits, 'kT')
    _require_positive_yield(yld)
    height = convert_units(h, dunits, 'm')
    sh = scale_height(yld, height)
    overp = convert_units(op, opunits, 'kg/cm^2')
    _require_positive_scalar(overp, 'op')
    lower, upper = _soviet_range_bounds(sh, thermal_layer)

    if thermal_layer:
        forward = lambda sr: _sovietnomach(sh, sr)
    else:
        forward = lambda sr: _sovietmach(sh, sr)

    lower_op = forward(lower)
    upper_op = forward(upper)

    if overp > lower_op or overp < upper_op:
        raise ValueOutsideGraphError(overp)
    if np.isclose(overp, lower_op):
        scaled_range = lower
    elif np.isclose(overp, upper_op):
        scaled_range = upper
    else:
        scaled_range = brentq(lambda sr: forward(sr) - overp, lower, upper)

    return convert_units(scaled_range * yld**(1.0 / 3), 'm', dunits)

def soviet_overpressure_points(target_op, y, h, n=100, thermal_layer=True, yunits='kT', dunits='m', opunits='kg/cm^2'):
    """反算满足目标静超压的爆心距离。

    虽然函数名沿用了“points”的提法，这里返回的是单个距离值。参数 `n`
    仅为保持接口一致性而保留。
    """
    _require_positive_scalar(target_op, 'target_op')
    _require_sample_count(n)
    return float(r_soviet_overpressure(
        y,
        target_op,
        h,
        thermal_layer=thermal_layer,
        yunits=yunits,
        dunits=dunits,
        opunits=opunits,
    ))
