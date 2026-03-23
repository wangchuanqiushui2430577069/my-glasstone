package com.glasstone.thermal;

import com.glasstone.exception.ValueOutsideGraphException;
import com.glasstone.math.Interpolation;
import com.glasstone.model.BurstParameters;
import com.glasstone.units.DistanceUnit;
import com.glasstone.units.UnitConverter;
import com.glasstone.units.YieldUnit;
import org.hipparchus.util.FastMath;

/**
 * 苏联热辐射经验模型。
 * <p>
 * 该实现基于原始图表数据做对数域插值，并支持地爆/空爆总热冲量的正算与反算。
 */
public final class ThermalCalculator {
    /**
     * 空爆热冲量与地爆热冲量在对数域中的经验偏移量。
     */
    private static final double AIR_TO_GROUND_LOG_OFFSET = 0.48287d;

    private static final double[][] THERMAL_X = {
            {},
            {-0.80410033, -0.6968039, -0.6143937, -0.5257837, -0.4213608, -0.326058, -0.23284414, -0.14691047, -0.041914154, 0.050766293, 0.14426279, 0.23704077, 0.33725953, 0.43328968, 0.51930285, 0.61267793, 0.7173376, 0.8061121, 0.8985058, 0.9845723, 1.0632958, 1.1452274, 1.2207355, 1.291724, 1.364926, 1.4221793, 1.4872091, 1.5449605, 1.6022445},
            {-0.80410033, -0.6968039, -0.6143937, -0.5257837, -0.4213608, -0.326058, -0.23284414, -0.14691047, -0.041914154, 0.050766293, 0.14426279, 0.23704077, 0.33725953, 0.43328968, 0.51772356, 0.58557355, 0.6696887, 0.75572246, 0.844042, 0.92272544, 1.0051376, 1.0729113, 1.1370374, 1.2089517, 1.2720737, 1.3293775, 1.3838692, 1.4339138, 1.4843141},
            {-0.80410033, -0.6968039, -0.6143937, -0.5257837, -0.4213608, -0.326058, -0.23284414, -0.14691047, -0.041914154, 0.03941413, 0.12613142, 0.22271647, 0.3126004, 0.39811367, 0.4801507, 0.55473137, 0.6339731, 0.7180863, 0.79000354, 0.8755243, 0.93921965, 1.0043213, 1.0757294, 1.1379868, 1.1966183, 1.2489536, 1.302569, 1.3489276, 1.3939435},
            {-0.80410033, -0.6968039, -0.6143937, -0.5257837, -0.4213608, -0.326058, -0.23284414, -0.14691047, -0.041914154, 0.031004282, 0.11025293, 0.20221579, 0.2846563, 0.3767594, 0.44731313, 0.52009034, 0.59106463, 0.67108023, 0.7429607, 0.81117266, 0.86165386, 0.92262167, 0.9802761, 1.0408001, 1.0939817, 1.1440446, 1.1944588, 1.237594, 1.2848366},
            {-0.80410033, -0.6968039, -0.6143937, -0.5257837, -0.4213608, -0.326058, -0.23284414, -0.14630179, -0.06752623, 0.017450733, 0.09201845, 0.17435059, 0.26316246, 0.33825722, 0.41329974, 0.48883262, 0.56026536, 0.62438524, 0.6999244, 0.75762373, 0.8071967, 0.8713394, 0.91041094, 0.9636462, 1.0141003, 1.0668474, 1.1168401, 1.1490343, 1.1913112},
            {-0.80410033, -0.6968039, -0.64206517, -0.5512937, -0.4596705, -0.3585259, -0.26841125, -0.18243463, -0.10347379, -0.012780763, 0.065952994, 0.14144976, 0.22762965, 0.30254737, 0.37125266, 0.44043675, 0.5018805, 0.56690866, 0.6277754, 0.6809697, 0.7383048, 0.78146815, 0.8309734, 0.8705795, 0.92012334, 0.9660478, 1.0087279, 1.0514227, 1.0836459},
            {-0.80410033, -0.6968039, -0.64206517, -0.5512937, -0.4596705, -0.3736596, -0.29157913, -0.21467015, -0.13312219, -0.05403929, 0.019946702, 0.09447114, 0.16643012, 0.23779498, 0.2955671, 0.35812527, 0.42226145, 0.4798631, 0.5340261, 0.5884958, 0.6385891, 0.6880637, 0.72762257, 0.7707784, 0.8143142, 0.84978783, 0.89292896, 0.9215304, 0.9565046},
            {-0.80410033, -0.6968039, -0.64206517, -0.55595523, -0.4723701, -0.3936186, -0.31515464, -0.24260396, -0.16685289, -0.10182351, -0.02365001, 0.035029277, 0.08849046, 0.15289961, 0.21378331, 0.26339933, 0.31659928, 0.36436334, 0.40790054, 0.44746813, 0.48685536, 0.5291736, 0.5614592, 0.6005373, 0.63316536, 0.65810686, 0.68295693, 0.70389295, 0.71416205},
            {-0.75696194, -0.6819367, -0.5968795, -0.52143353, -0.45842078, -0.38510278, -0.32330638, -0.2620127, -0.20134935, -0.1408617, -0.08830985, -0.04143613, 0.008174207, 0.06145246, 0.10822666, 0.15106326, 0.18977095, 0.22865695, 0.26054838, 0.303628, 0.3304138, 0.36267093, 0.3941013, 0.42308193, 0.44435713, 0.4656802, 0.4864305, 0.4931791}
    };

    private static final double[][] THERMAL_Y = {
            {},
            {1.988, 3.116, 3.94, 4.938, 6.006, 7.028, 8.016, 8.987, 10.014, 11.009, 12.034, 13.025, 13.995, 15.017, 15.985, 16.95, 17.983, 18.97, 19.991, 21.005, 21.968, 22.985, 24.034, 25.019, 26.019, 27.07, 28.1, 29.098, 30.099},
            {1.988, 3.116, 3.94, 4.938, 6.006, 7.028, 8.016, 8.987, 10.014, 11.009, 12.034, 13.025, 13.995, 15.017, 16.11, 16.921, 17.944, 19.004, 19.991, 21.011, 22.005, 23.027, 24.022, 24.988, 26.021, 27.016, 28.1, 29.041, 30.103},
            {1.988, 3.116, 3.94, 4.938, 6.006, 7.028, 8.016, 8.987, 10.014, 10.986, 12.018, 13.021, 13.985, 15.032, 15.966, 16.925, 17.922, 18.973, 19.95, 21.035, 22.026, 22.969, 23.999, 25.008, 26.041, 27.013, 28.074, 29.071, 30.098},
            {1.988, 3.116, 3.94, 4.938, 6.006, 7.028, 8.016, 8.987, 10.014, 11.034, 12.035, 13.004, 13.968, 15.007, 15.996, 16.966, 17.974, 18.975, 19.971, 21.038, 21.949, 22.947, 24.04, 25.038, 26.07, 26.996, 28.057, 29.058, 30.085},
            {1.988, 3.116, 3.94, 4.938, 6.006, 7.028, 8.016, 9.011, 9.97, 11.017, 12.056, 12.989, 14.007, 14.996, 15.959, 16.963, 17.959, 18.981, 20.0, 20.994, 22.014, 23.01, 24.002, 24.993, 26.016, 27.018, 28.102, 29.082, 30.112},
            {1.988, 3.116, 3.924, 4.935, 6.006, 6.987, 7.948, 8.995, 9.959, 10.976, 12.004, 12.977, 14.006, 15.055, 15.998, 16.948, 17.94, 18.931, 19.983, 21.034, 22.002, 22.965, 24.053, 24.965, 25.989, 27.032, 28.058, 29.085, 30.111},
            {1.988, 3.116, 3.924, 4.935, 6.006, 6.973, 7.956, 8.991, 9.985, 11.016, 12.028, 13.044, 14.003, 15.003, 16.027, 16.967, 17.966, 18.971, 19.973, 21.012, 21.972, 23.011, 24.003, 25.004, 26.087, 27.015, 28.095, 29.056, 30.051},
            {1.988, 3.116, 3.924, 4.915, 5.996, 6.982, 7.972, 8.975, 9.972, 10.968, 12.025, 13.05, 13.987, 14.986, 16.034, 16.968, 17.962, 19.008, 20.005, 20.967, 21.997, 23.042, 23.974, 24.963, 25.992, 26.99, 28.037, 29.024, 30.089},
            {2.963, 3.922, 4.984, 6.037, 7.002, 7.976, 8.997, 9.992, 11.018, 12.069, 13.025, 13.992, 14.975, 15.973, 16.932, 17.989, 18.974, 19.963, 21.011, 21.997, 22.979, 23.963, 24.985, 26.01, 27.031, 28.028, 29.022, 30.067}
    };

    private ThermalCalculator() {
    }

    /**
     * 计算苏联模型下空爆总热冲量。
     *
     * @param burst 爆炸参数
     * @param range 斜距
     * @param rangeUnit 距离单位
     * @param visibilityCode 国际能见度代码
     * @return 总热冲量
     */
    public static double sovietAirThermal(
            BurstParameters burst,
            double range,
            DistanceUnit rangeUnit,
            int visibilityCode
    ) {
        // slantRangeKm: 统一换算到公里；modelVisibility: 参与插值的图表编号。
        double slantRangeKm = UnitConverter.convertDistance(range, rangeUnit, DistanceUnit.KILOMETERS);
        double yieldKt = UnitConverter.convertYield(burst.yield(), burst.yieldUnit(), YieldUnit.KILOTONS);
        int modelVisibility = toModelVisibility(visibilityCode, yieldKt, burst.burstHeight());
        return sovietAirThermalInternal(slantRangeKm, yieldKt, modelVisibility);
    }

    /**
     * 计算苏联模型下地爆总热冲量。
     *
     * @param burst 爆炸参数
     * @param range 斜距
     * @param rangeUnit 距离单位
     * @param visibilityCode 国际能见度代码
     * @return 总热冲量
     */
    public static double sovietGroundThermal(
            BurstParameters burst,
            double range,
            DistanceUnit rangeUnit,
            int visibilityCode
    ) {
        double slantRangeKm = UnitConverter.convertDistance(range, rangeUnit, DistanceUnit.KILOMETERS);
        double yieldKt = UnitConverter.convertYield(burst.yield(), burst.yieldUnit(), YieldUnit.KILOTONS);
        int modelVisibility = toModelVisibility(visibilityCode, yieldKt, burst.burstHeight());
        return sovietGroundThermalInternal(slantRangeKm, yieldKt, modelVisibility);
    }

    /**
     * 根据给定空爆热冲量反推斜距。
     *
     * @param burst 爆炸参数
     * @param fluence 总热冲量
     * @param visibilityCode 国际能见度代码
     * @param outputUnit 输出距离单位
     * @return 反推出的斜距
     */
    public static double inverseSovietAirThermal(
            BurstParameters burst,
            double fluence,
            int visibilityCode,
            DistanceUnit outputUnit
    ) {
        double yieldKt = UnitConverter.convertYield(burst.yield(), burst.yieldUnit(), YieldUnit.KILOTONS);
        int modelVisibility = toModelVisibility(visibilityCode, yieldKt, burst.burstHeight());
        double rangeKm = reverseSovietAirThermalInternal(fluence, yieldKt, modelVisibility);
        return UnitConverter.convertDistance(rangeKm, DistanceUnit.KILOMETERS, outputUnit);
    }

    /**
     * 根据给定地爆热冲量反推斜距。
     *
     * @param burst 爆炸参数
     * @param fluence 总热冲量
     * @param visibilityCode 国际能见度代码
     * @param outputUnit 输出距离单位
     * @return 反推出的斜距
     */
    public static double inverseSovietGroundThermal(
            BurstParameters burst,
            double fluence,
            int visibilityCode,
            DistanceUnit outputUnit
    ) {
        double yieldKt = UnitConverter.convertYield(burst.yield(), burst.yieldUnit(), YieldUnit.KILOTONS);
        int modelVisibility = toModelVisibility(visibilityCode, yieldKt, burst.burstHeight());
        double rangeKm = reverseSovietGroundThermalInternal(fluence, yieldKt, modelVisibility);
        return UnitConverter.convertDistance(rangeKm, DistanceUnit.KILOMETERS, outputUnit);
    }

    /**
     * 在空爆图表上进行正向计算。
     */
    private static double sovietAirThermalInternal(double scaleRangeKm, double actualYieldKt, int modelVisibility) {
        return reverseThermalSlope(sovietCurveValue(modelVisibility, scaleRangeKm), actualYieldKt);
    }

    /**
     * 在地爆图表上进行正向计算。
     */
    private static double sovietGroundThermalInternal(double scaleRangeKm, double actualYieldKt, int modelVisibility) {
        return airToGround(sovietAirThermalInternal(scaleRangeKm, actualYieldKt, modelVisibility));
    }

    /**
     * 在空爆条件下反推斜距。
     */
    private static double reverseSovietAirThermalInternal(double impulse, double actualYieldKt, int modelVisibility) {
        requirePositive(impulse);
        double y = thermalSlope(impulse, actualYieldKt);
        return reverseCurveRange(modelVisibility, y);
    }

    /**
     * 在地爆条件下反推斜距。
     */
    private static double reverseSovietGroundThermalInternal(double impulse, double actualYieldKt, int modelVisibility) {
        requirePositive(impulse);
        return reverseSovietAirThermalInternal(groundToAir(impulse), actualYieldKt, modelVisibility);
    }

    /**
     * 从图表中读取指定能见度、指定距离的曲线值。
     */
    private static double sovietCurveValue(int modelVisibility, double scaleRangeKm) {
        requireModelVisibility(modelVisibility);
        requirePositive(scaleRangeKm);
        double[] xs = THERMAL_X[modelVisibility];
        double[] ys = THERMAL_Y[modelVisibility];
        // logRange: 热图表在对数距离坐标上给出。
        double logRange = FastMath.log10(scaleRangeKm);
        validateRange(logRange, xs[0], xs[xs.length - 1], scaleRangeKm);
        return Interpolation.interpolate(logRange, xs, ys);
    }

    /**
     * 根据图表纵坐标反求原始斜距。
     */
    private static double reverseCurveRange(int modelVisibility, double y) {
        requireModelVisibility(modelVisibility);
        double[] xs = THERMAL_X[modelVisibility];
        double[] ys = THERMAL_Y[modelVisibility];
        validateRange(y, ys[0], ys[ys.length - 1], y);
        return FastMath.pow(10.0d, Interpolation.interpolate(y, ys, xs));
    }

    /**
     * 把热冲量转换成图表使用的直线拟合变量。
     */
    private static double thermalSlope(double impulse, double actualYieldKt) {
        requirePositive(impulse);
        requirePositive(actualYieldKt);
        return 12.83d - (4.93d * FastMath.log10(impulse)) + (5.15d * FastMath.log10(actualYieldKt));
    }

    /**
     * 从图表拟合变量反推热冲量。
     */
    private static double reverseThermalSlope(double y, double actualYieldKt) {
        requirePositive(actualYieldKt);
        return FastMath.pow(10.0d, (y - 12.83d - (5.15d * FastMath.log10(actualYieldKt))) / -4.93d);
    }

    /**
     * 把空爆热冲量换算为地爆热冲量。
     */
    private static double airToGround(double impulse) {
        requirePositive(impulse);
        return FastMath.pow(10.0d, FastMath.log10(impulse) - AIR_TO_GROUND_LOG_OFFSET);
    }

    /**
     * 把地爆热冲量换算为空爆热冲量。
     */
    private static double groundToAir(double impulse) {
        requirePositive(impulse);
        return FastMath.pow(10.0d, FastMath.log10(impulse) + AIR_TO_GROUND_LOG_OFFSET);
    }

    /**
     * 将国际能见度代码映射到苏联热图表的编号。
     */
    private static int toModelVisibility(int visibilityCode, double actualYieldKt, double burstHeight) {
        requireVisibilityCode(visibilityCode);
        if (visibilityCode == 9) {
            return 1;
        }

        // groundburst: 地爆情况下的图表编号。
        int groundburst = 10 - visibilityCode;
        if (visibilityCode == 2 || visibilityCode == 3) {
            groundburst = groundburst + 1;
        }

        if (burstHeight == 0.0d || actualYieldKt <= 100.0d) {
            return groundburst;
        }

        return groundburst - 1;
    }

    /**
     * 校验图表编号是否合法。
     */
    private static void requireModelVisibility(int modelVisibility) {
        if (modelVisibility < 1 || modelVisibility > 9) {
            throw new ValueOutsideGraphException(modelVisibility);
        }
    }

    /**
     * 校验国际能见度代码是否合法。
     */
    private static void requireVisibilityCode(int visibilityCode) {
        if (visibilityCode < 1 || visibilityCode > 9) {
            throw new ValueOutsideGraphException(visibilityCode);
        }
    }

    /**
     * 校验输入是否为正数。
     */
    private static void requirePositive(double value) {
        if (!(value > 0.0d) || Double.isNaN(value) || Double.isInfinite(value)) {
            throw new ValueOutsideGraphException(value);
        }
    }

    /**
     * 校验插值变量是否落在图表范围内。
     */
    private static void validateRange(double value, double minimum, double maximum, Object sourceValue) {
        if (value < minimum || value > maximum || Double.isNaN(value)) {
            throw new ValueOutsideGraphException(sourceValue);
        }
    }
}
