package com.glasstone.overpressure;

import com.glasstone.exception.ValueOutsideGraphException;
import com.glasstone.math.Interpolation;
import com.glasstone.model.BurstParameters;
import com.glasstone.units.DistanceUnit;
import com.glasstone.units.PressureUnit;
import com.glasstone.units.UnitConverter;
import com.glasstone.units.YieldUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import org.hipparchus.util.FastMath;

/**
 * 冲击波与超压计算器。
 * <p>
 * 当前实现同时封装了 Brode、美军 DNA 以及苏联经验图表三套模型。
 */
public final class OverpressureCalculator {
    private static final double MIN_SOLVER_RANGE_METERS = 1.0e-6d;
    private static final double MAX_SOLVER_RANGE_METERS = 1_000_000_000.0d;
    private static final int RANGE_SCAN_STEPS = 768;
    private static final int RANGE_BISECTION_STEPS = 80;

    private OverpressureCalculator() {
    }

    /**
     * 使用 Brode 模型计算峰值静超压。
     *
     * @param burst 爆炸参数
     * @param range 地面距离
     * @param rangeUnit 距离单位
     * @param outputUnit 输出压力单位
     * @return 峰值静超压
     */
    public static double brodeOverpressure(
            BurstParameters burst,
            double range,
            DistanceUnit rangeUnit,
            PressureUnit outputUnit
    ) {
        requirePositiveRange(range);
        // yieldKt: 当量统一到千吨；groundRangeKilofeet/burstHeightKilofeet: Brode 公式的原生单位。
        double yieldKt = UnitConverter.convertYield(burst.yield(), burst.yieldUnit(), YieldUnit.KILOTONS);
        double groundRangeKilofeet = UnitConverter.convertDistance(range, rangeUnit, DistanceUnit.KILOFEET);
        double burstHeightKilofeet = UnitConverter.convertDistance(burst.burstHeight(), burst.distanceUnit(), DistanceUnit.KILOFEET);
        double overpressurePsi = brodeOverpressurePsi(yieldKt, groundRangeKilofeet, burstHeightKilofeet);
        return UnitConverter.convertPressure(overpressurePsi, PressureUnit.PSI, outputUnit);
    }

    /**
     * 使用 DNA 模型计算峰值静超压。
     */
    public static double dnaStaticOverpressure(
            BurstParameters burst,
            double range,
            DistanceUnit rangeUnit,
            PressureUnit outputUnit
    ) {
        requirePositiveRange(range);
        // groundRangeMeters / burstHeightMeters: DNA 模型使用米制输入。
        double yieldKt = UnitConverter.convertYield(burst.yield(), burst.yieldUnit(), YieldUnit.KILOTONS);
        double groundRangeMeters = UnitConverter.convertDistance(range, rangeUnit, DistanceUnit.METERS);
        double burstHeightMeters = UnitConverter.convertDistance(burst.burstHeight(), burst.distanceUnit(), DistanceUnit.METERS);
        double overpressurePa = dnaAirburstPeakOverpressure(groundRangeMeters, yieldKt, burstHeightMeters);
        return UnitConverter.convertPressure(overpressurePa, PressureUnit.PASCAL, outputUnit);
    }

    /**
     * 使用 DNA 模型计算峰值动压。
     */
    public static double dnaDynamicPressure(
            BurstParameters burst,
            double range,
            DistanceUnit rangeUnit,
            PressureUnit outputUnit
    ) {
        requirePositiveRange(range);
        double yieldKt = UnitConverter.convertYield(burst.yield(), burst.yieldUnit(), YieldUnit.KILOTONS);
        double groundRangeMeters = UnitConverter.convertDistance(range, rangeUnit, DistanceUnit.METERS);
        double burstHeightMeters = UnitConverter.convertDistance(burst.burstHeight(), burst.distanceUnit(), DistanceUnit.METERS);
        double dynamicPressurePa = dnaAirburstPeakDynamicPressure(groundRangeMeters, yieldKt, burstHeightMeters);
        return UnitConverter.convertPressure(dynamicPressurePa, PressureUnit.PASCAL, outputUnit);
    }

    /**
     * 使用苏联图表模型计算峰值静超压。
     *
     * @param thermalLayer 是否采用“考虑热前驱层”的分支
     */
    public static double sovietOverpressure(
            BurstParameters burst,
            double range,
            DistanceUnit rangeUnit,
            boolean thermalLayer,
            PressureUnit outputUnit
    ) {
        double yieldKt = UnitConverter.convertYield(burst.yield(), burst.yieldUnit(), YieldUnit.KILOTONS);
        double groundRangeMeters = UnitConverter.convertDistance(range, rangeUnit, DistanceUnit.METERS);
        double burstHeightMeters = UnitConverter.convertDistance(burst.burstHeight(), burst.distanceUnit(), DistanceUnit.METERS);
        // scaleRange / scaleHeight: 苏联图表按立方根相似律缩比后的距离与爆高。
        double scaleRange = scaledRange(yieldKt, groundRangeMeters);
        double scaleHeight = scaledHeight(yieldKt, burstHeightMeters);
        double overpressureKgPerCm2 = thermalLayer
                ? sovietNoMach(scaleHeight, scaleRange)
                : sovietMach(scaleHeight, scaleRange);
        return UnitConverter.convertPressure(overpressureKgPerCm2, PressureUnit.KILOGRAM_PER_CM2, outputUnit);
    }

    /**
     * 根据苏联图表反求达到指定超压所需的地面距离。
     */
    public static double inverseSovietOverpressure(
            BurstParameters burst,
            double overpressure,
            PressureUnit overpressureUnit,
            boolean thermalLayer,
            DistanceUnit outputUnit
    ) {
        double yieldKt = UnitConverter.convertYield(burst.yield(), burst.yieldUnit(), YieldUnit.KILOTONS);
        double burstHeightMeters = UnitConverter.convertDistance(burst.burstHeight(), burst.distanceUnit(), DistanceUnit.METERS);
        double scaleHeight = scaledHeight(yieldKt, burstHeightMeters);
        double overpressureKgPerCm2 = UnitConverter.convertPressure(overpressure, overpressureUnit, PressureUnit.KILOGRAM_PER_CM2);
        double scaleRange = thermalLayer
                ? inverseSovietNoMach(scaleHeight, overpressureKgPerCm2)
                : inverseSovietMach(scaleHeight, overpressureKgPerCm2);
        return UnitConverter.convertDistance(scaleRange * FastMath.cbrt(yieldKt), DistanceUnit.METERS, outputUnit);
    }

    /**
     * 根据 Brode 模型数值反求达到指定超压所需的地面距离。
     * <p>
     * 若局部存在多个交点，默认返回离爆点最近的那个距离。
     */
    public static double inverseBrodeOverpressure(
            BurstParameters burst,
            double overpressure,
            PressureUnit overpressureUnit,
            DistanceUnit outputUnit
    ) {
        requirePositiveOverpressure(overpressure);
        double yieldKt = UnitConverter.convertYield(burst.yield(), burst.yieldUnit(), YieldUnit.KILOTONS);
        double burstHeightKilofeet = UnitConverter.convertDistance(
                burst.burstHeight(),
                burst.distanceUnit(),
                DistanceUnit.KILOFEET
        );
        double targetPsi = UnitConverter.convertPressure(overpressure, overpressureUnit, PressureUnit.PSI);
        double rangeMeters = inverseOverpressureRangeMeters(
                targetPsi,
                candidateRangeMeters -> brodeOverpressurePsi(
                        yieldKt,
                        UnitConverter.convertDistance(candidateRangeMeters, DistanceUnit.METERS, DistanceUnit.KILOFEET),
                        burstHeightKilofeet
                ),
                "Brode"
        );
        return UnitConverter.convertDistance(rangeMeters, DistanceUnit.METERS, outputUnit);
    }

    /**
     * 根据 Brode 模型数值反求达到指定超压所需的地面距离，并允许指定搜索采样密度。
     * <p>
     * `n` 与 Python 版 `brode_overpressure_distances(..., n=...)` 对齐：内部会使用
     * `max(n * 4, 200)` 个对数采样点搜索符号变化区间。
     */
    public static double inverseBrodeOverpressure(
            BurstParameters burst,
            double overpressure,
            PressureUnit overpressureUnit,
            DistanceUnit outputUnit,
            int n
    ) {
        requirePositiveOverpressure(overpressure);
        int rangeScanSteps = rangeScanStepsFromSampleCount(n);
        double yieldKt = UnitConverter.convertYield(burst.yield(), burst.yieldUnit(), YieldUnit.KILOTONS);
        double burstHeightKilofeet = UnitConverter.convertDistance(
                burst.burstHeight(),
                burst.distanceUnit(),
                DistanceUnit.KILOFEET
        );
        double targetPsi = UnitConverter.convertPressure(overpressure, overpressureUnit, PressureUnit.PSI);
        double rangeMeters = inverseOverpressureRangeMeters(
                targetPsi,
                candidateRangeMeters -> brodeOverpressurePsi(
                        yieldKt,
                        UnitConverter.convertDistance(candidateRangeMeters, DistanceUnit.METERS, DistanceUnit.KILOFEET),
                        burstHeightKilofeet
                ),
                "Brode",
                rangeScanSteps
        );
        return UnitConverter.convertDistance(rangeMeters, DistanceUnit.METERS, outputUnit);
    }

    /**
     * 根据 DNA 模型数值反求达到指定峰值静超压所需的地面距离。
     */
    public static double inverseDnaStaticOverpressure(
            BurstParameters burst,
            double overpressure,
            PressureUnit overpressureUnit,
            DistanceUnit outputUnit
    ) {
        requirePositiveOverpressure(overpressure);
        double yieldKt = UnitConverter.convertYield(burst.yield(), burst.yieldUnit(), YieldUnit.KILOTONS);
        double burstHeightMeters = UnitConverter.convertDistance(
                burst.burstHeight(),
                burst.distanceUnit(),
                DistanceUnit.METERS
        );
        double targetPa = UnitConverter.convertPressure(overpressure, overpressureUnit, PressureUnit.PASCAL);
        double rangeMeters = inverseOverpressureRangeMeters(
                targetPa,
                range -> dnaAirburstPeakOverpressure(range, yieldKt, burstHeightMeters),
                "DNA static"
        );
        return UnitConverter.convertDistance(rangeMeters, DistanceUnit.METERS, outputUnit);
    }

    /**
     * 根据 DNA 模型数值反求达到指定峰值动压所需的地面距离。
     * <p>
     * 当同一目标值存在内外两条解时，默认返回离爆点最近的那个距离。
     */
    public static double inverseDnaDynamicPressure(
            BurstParameters burst,
            double pressure,
            PressureUnit pressureUnit,
            DistanceUnit outputUnit
    ) {
        requirePositiveOverpressure(pressure);
        double yieldKt = UnitConverter.convertYield(burst.yield(), burst.yieldUnit(), YieldUnit.KILOTONS);
        double burstHeightMeters = UnitConverter.convertDistance(
                burst.burstHeight(),
                burst.distanceUnit(),
                DistanceUnit.METERS
        );
        double targetPa = UnitConverter.convertPressure(pressure, pressureUnit, PressureUnit.PASCAL);
        double rangeMeters = inverseOverpressureRangeMeters(
                targetPa,
                range -> dnaAirburstPeakDynamicPressure(range, yieldKt, burstHeightMeters),
                "DNA dynamic"
        );
        return UnitConverter.convertDistance(rangeMeters, DistanceUnit.METERS, outputUnit);
    }

    /**
     * 把 Brode 模型输入变换为无量纲变量后进行求值。
     */
    private static double brodeOverpressurePsi(double yieldKt, double groundRangeKilofeet, double burstHeightKilofeet) {
        // z: 爆高/地面距离比值；slantRange: 缩比后的斜距。
        double z = burstHeightKilofeet / groundRangeKilofeet;
        double scaledHeight = scaledHeight(yieldKt, burstHeightKilofeet);
        double scaledRange = scaledRange(yieldKt, groundRangeKilofeet);
        double slantRange = FastMath.hypot(scaledRange, scaledHeight);
        return brode(z, slantRange, scaledHeight);
    }

    /**
     * Brode 经验多项式/有理式主函数。
     */
    private static double brode(double z, double r, double y) {
        // a~k: Brode 文献中的拟合系数。
        double a = 1.22d - ((3.908d * FastMath.pow(z, 2.0d)) / (1.0d + 810.2d * FastMath.pow(z, 5.0d)));
        double b = 2.321d
                + ((6.195d * FastMath.pow(z, 18.0d)) / (1.0d + 1.113d * FastMath.pow(z, 18.0d)))
                - ((0.03831d * FastMath.pow(z, 17.0d)) / (1.0d + 0.02415d * FastMath.pow(z, 17.0d)))
                + (0.6692d / (1.0d + 4164.0d * FastMath.pow(z, 8.0d)));
        double c = 4.153d
                - ((1.149d * FastMath.pow(z, 18.0d)) / (1.0d + 1.641d * FastMath.pow(z, 18.0d)))
                - (1.1d / (1.0d + 2.771d * FastMath.pow(z, 2.5d)));
        double d = -4.166d
                + ((25.76d * FastMath.pow(z, 1.75d)) / (1.0d + 1.382d * FastMath.pow(z, 18.0d)))
                + ((8.257d * z) / (1.0d + 3.219d * z));
        double e = 1.0d - ((0.004642d * FastMath.pow(z, 18.0d)) / (1.0d + 0.003886d * FastMath.pow(z, 18.0d)));
        double f = 0.6096d
                + ((2.879d * FastMath.pow(z, 9.25d)) / (1.0d + 2.359d * FastMath.pow(z, 14.5d)))
                - ((17.5d * FastMath.pow(z, 2.0d)) / (1.0d + 71.66d * FastMath.pow(z, 3.0d)));
        double g = 1.83d + ((5.361d * FastMath.pow(z, 2.0d)) / (1.0d + 0.3139d * FastMath.pow(z, 6.0d)));
        double h = ((8.808d * FastMath.pow(z, 1.5d)) / (1.0d + 154.5d * FastMath.pow(z, 3.5d)))
                - ((0.2905d + 64.67d * FastMath.pow(z, 5.0d)) / (1.0d + 441.5d * FastMath.pow(z, 5.0d)))
                - ((1.389d * z) / (1.0d + 49.03d * FastMath.pow(z, 5.0d)))
                + ((1.094d * FastMath.pow(r, 2.0d))
                / ((781.2d - (123.4d * r) + (37.98d * FastMath.pow(r, 1.5d)) + FastMath.pow(r, 2.0d)) * (1.0d + (2.0d * y))));
        double j = ((0.000629d * FastMath.pow(y, 4.0d)) / (3.493e-9d + FastMath.pow(y, 4.0d)))
                - ((2.67d * FastMath.pow(y, 2.0d)) / (1.0d + (1.0e7d * FastMath.pow(y, 4.3d))));
        double k = 5.18d + ((0.2803d * FastMath.pow(y, 3.5d)) / (3.788e-6d + FastMath.pow(y, 4.0d)));
        return (10.47d / FastMath.pow(r, a))
                + (b / FastMath.pow(r, c))
                + ((d * e) / (1.0d + (f * FastMath.pow(r, g))))
                + h
                + (j / FastMath.pow(r, k));
    }

    /**
     * DNA 模型下的峰值静超压，内部会在常规反射与 Mach 反射之间平滑切换。
     */
    private static double dnaAirburstPeakOverpressure(double rangeMeters, double yieldKt, double altitudeMeters) {
        double sigma = regularMachSwitchingParameter(rangeMeters, yieldKt, altitudeMeters);
        if (sigma == 0.0d) {
            return pMach(rangeMeters, yieldKt, altitudeMeters);
        }
        if (sigma == 1.0d) {
            return pReg(rangeMeters, yieldKt, altitudeMeters);
        }
        return pReg(rangeMeters, yieldKt, altitudeMeters) * sigma
                + pMach(rangeMeters, yieldKt, altitudeMeters) * (1.0d - sigma);
    }

    /**
     * DNA 模型下的峰值动压。
     */
    private static double dnaAirburstPeakDynamicPressure(double rangeMeters, double yieldKt, double altitudeMeters) {
        // pair: 峰值静压；sigma: 反射区切换参数；alpha: 入射角。
        double pair = dnaAirburstPeakOverpressure(rangeMeters, yieldKt, altitudeMeters);
        double sigma = regularMachSwitchingParameter(rangeMeters, yieldKt, altitudeMeters);
        double sgr = scaledRange(yieldKt, rangeMeters);
        double shob = scaledHeight(yieldKt, altitudeMeters);
        double alpha = FastMath.atan(shob / sgr);
        double densityRatio = massDensityRatio(pair);
        return 0.5d * pair * (densityRatio - 1.0d) * (1.0d - (sigma * FastMath.pow(FastMath.sin(alpha), 2.0d)));
    }

    /**
     * 1kT 自由空爆的参考超压曲线。
     */
    private static double dna1KtFreeAirOverpressure(double slantRangeMeters) {
        // scaled: 归一化斜距；logTerm: DNA 公式中的对数修正项。
        double scaled = slantRangeMeters / 445.42d;
        double logTerm = FastMath.log(scaled + 3.0d * FastMath.exp(FastMath.sqrt(scaled) / -3.0d));
        return (3.04e11d / FastMath.pow(slantRangeMeters, 3.0d))
                + (1.13e9d / FastMath.pow(slantRangeMeters, 2.0d))
                + (7.9e6d / (slantRangeMeters * FastMath.sqrt(logTerm)));
    }

    /**
     * 把超压换成冲击波强度比。
     */
    private static double shockStrength(double overpressure) {
        return (overpressure / 101325.0d) + 1.0d;
    }

    /**
     * 计算冲击波有效比热比。
     */
    private static double shockGamma(double overpressure) {
        double xi = shockStrength(overpressure);
        double t = 1.0e-12d * FastMath.pow(xi, 6.0d);
        double z = FastMath.log(xi) - ((0.47d * t) / (100.0d + t));
        return 1.402d - ((3.4e-4d * FastMath.pow(z, 4.0d)) / (1.0d + 2.22e-5d * FastMath.pow(z, 6.0d)));
    }

    private static double shockMu(double gamma) {
        return (gamma + 1.0d) / (gamma - 1.0d);
    }

    private static double massDensityRatio(double overpressure) {
        double xi = shockStrength(overpressure);
        double mu = shockMu(shockGamma(overpressure));
        return (1.0d + mu * xi) / (5.975d + xi);
    }

    private static double scaledSlantRange(double rangeMeters, double yieldKt, double altitudeMeters) {
        return FastMath.hypot(scaledRange(yieldKt, rangeMeters), scaledHeight(yieldKt, altitudeMeters));
    }

    /**
     * 计算常规反射与 Mach 反射交界的角度。
     */
    private static double regularMachMergeAngle(double rangeMeters, double yieldKt, double altitudeMeters) {
        double overpressure = dna1KtFreeAirOverpressure(scaledSlantRange(rangeMeters, yieldKt, altitudeMeters));
        double t = 340.0d / FastMath.pow(overpressure, 0.55d);
        double u = 1.0d / ((7782.0d * FastMath.pow(overpressure, 0.7d)) + 0.9d);
        return FastMath.atan(1.0d / (t + u));
    }

    /**
     * 计算反射区切换带宽。
     */
    private static double mergeRegionWidth(double rangeMeters, double yieldKt, double altitudeMeters) {
        double overpressure = dna1KtFreeAirOverpressure(scaledSlantRange(rangeMeters, yieldKt, altitudeMeters));
        double t = 340.0d / FastMath.pow(overpressure, 0.55d);
        double w = 1.0d / ((7473.0d * FastMath.pow(overpressure, 0.5d)) + 6.6d);
        double v = 1.0d / ((647.0d * FastMath.pow(overpressure, 0.8d)) + w);
        return FastMath.atan(1.0d / (t + v));
    }

    /**
     * 计算常规反射区与 Mach 反射区之间的平滑切换参数。
     */
    private static double regularMachSwitchingParameter(double rangeMeters, double yieldKt, double altitudeMeters) {
        // s: 原始切换参数；s0: 限幅后的切换参数。
        double sgr = scaledRange(yieldKt, rangeMeters);
        double shob = scaledHeight(yieldKt, altitudeMeters);
        double alpha = FastMath.atan(shob / sgr);
        double s = (alpha - regularMachMergeAngle(rangeMeters, yieldKt, altitudeMeters))
                / mergeRegionWidth(rangeMeters, yieldKt, altitudeMeters);
        double s0 = clamp(s, -1.0d, 1.0d);
        return 0.5d * (FastMath.sin(0.5d * FastMath.PI * s0) + 1.0d);
    }

    /**
     * Mach 反射区的峰值超压。
     */
    private static double pMach(double rangeMeters, double yieldKt, double altitudeMeters) {
        double sgr = scaledRange(yieldKt, rangeMeters);
        double shob = scaledHeight(yieldKt, altitudeMeters);
        double alpha = FastMath.atan(shob / sgr);
        // a/b/c: Mach 反射经验修正项。
        double a = FastMath.min(3.7d - (0.94d * FastMath.log(sgr)), 0.7d);
        double b = (0.77d * FastMath.log(sgr)) - 3.8d - (18.0d / sgr);
        double c = FastMath.max(a, b);
        return dna1KtFreeAirOverpressure(sgr / FastMath.cbrt(2.0d)) / (1.0d - c * FastMath.sin(alpha));
    }

    /**
     * 常规反射区的峰值超压。
     */
    private static double pReg(double rangeMeters, double yieldKt, double altitudeMeters) {
        double sgr = scaledRange(yieldKt, rangeMeters);
        double freeAir = dna1KtFreeAirOverpressure(scaledSlantRange(rangeMeters, yieldKt, altitudeMeters));
        double shob = scaledHeight(yieldKt, altitudeMeters);
        double alpha = FastMath.atan(shob / sgr);
        // reflectionFactor: 反射放大因子；f/d: 角度分布修正项。
        double reflectionFactor = 2.0d + ((shockGamma(freeAir) + 1.0d) * (massDensityRatio(freeAir) - 1.0d)) / 2.0d;
        double f = freeAir / 75842.0d;
        double d = (FastMath.pow(f, 6.0d) * (1.2d + 0.07d * FastMath.sqrt(f))) / (FastMath.pow(f, 6.0d) + 1.0d);
        return freeAir * (((reflectionFactor - 2.0d) * FastMath.pow(FastMath.sin(alpha), d)) + 2.0d);
    }

    /**
     * 在苏联“有热前驱层”图表中正向查表。
     */
    private static double sovietMach(double scaleHeight, double scaleRange) {
        if (scaleHeight >= 120.0d && scaleHeight <= 200.0d) {
            return lerp10(scaleHeight, 120.0d, 200.0d,
                    interpolate(scaleRange, SovietOverpressureData.MACH_SH12),
                    interpolate(scaleRange, SovietOverpressureData.MACH_SH20));
        }
        if (scaleHeight >= 70.0d && scaleHeight < 120.0d) {
            return lerp10(scaleHeight, 70.0d, 120.0d,
                    interpolate(scaleRange, SovietOverpressureData.MACH_SH7),
                    interpolate(scaleRange, SovietOverpressureData.MACH_SH12));
        }
        if (scaleHeight >= 0.0d && scaleHeight < 70.0d) {
            return lerp10(scaleHeight, 0.0d, 70.0d,
                    interpolate(scaleRange, SovietOverpressureData.GROUND),
                    interpolate(scaleRange, SovietOverpressureData.MACH_SH7));
        }
        throw new ValueOutsideGraphException(scaleHeight);
    }

    /**
     * 在苏联“无热前驱层”图表中正向查表。
     */
    private static double sovietNoMach(double scaleHeight, double scaleRange) {
        if (scaleHeight > 120.0d && scaleHeight <= 200.0d) {
            return lerp10(scaleHeight, 120.0d, 200.0d,
                    interpolate(scaleRange, SovietOverpressureData.NOMACH_SH12),
                    interpolate(scaleRange, SovietOverpressureData.NOMACH_SH20));
        }
        if (scaleHeight >= 70.0d && scaleHeight <= 120.0d) {
            return lerp10(scaleHeight, 70.0d, 120.0d,
                    interpolate(scaleRange, SovietOverpressureData.NOMACH_SH7),
                    interpolate(scaleRange, SovietOverpressureData.NOMACH_SH12));
        }
        if (scaleHeight >= 0.0d && scaleHeight < 70.0d) {
            return lerp10(scaleHeight, 0.0d, 70.0d,
                    interpolate(scaleRange, SovietOverpressureData.GROUND),
                    interpolate(scaleRange, SovietOverpressureData.NOMACH_SH7));
        }
        throw new ValueOutsideGraphException(scaleHeight);
    }

    /**
     * 在苏联“无热前驱层”图表中反查距离。
     */
    private static double inverseSovietNoMach(double scaleHeight, double overpressureKgPerCm2) {
        double logOverpressure = FastMath.log10(overpressureKgPerCm2);
        if (scaleHeight >= 120.0d && overpressureKgPerCm2 > 2.975d) {
            return inverseHighOverpressure(scaleHeight, logOverpressure, true, 11);
        }
        if (scaleHeight >= 120.0d && scaleHeight <= 200.0d) {
            return Interpolation.linear(scaleHeight, 120.0d, 200.0d,
                    interpolate(logOverpressure, SovietOverpressureData.R_NOMACH_SH12),
                    interpolate(logOverpressure, SovietOverpressureData.R_NOMACH_SH20));
        }
        if (scaleHeight >= 70.0d && scaleHeight < 120.0d) {
            return Interpolation.linear(scaleHeight, 70.0d, 120.0d,
                    interpolate(logOverpressure, SovietOverpressureData.R_NOMACH_SH7),
                    interpolate(logOverpressure, SovietOverpressureData.R_NOMACH_SH12));
        }
        if (scaleHeight >= 0.0d && scaleHeight < 70.0d) {
            return Interpolation.linear(scaleHeight, 0.0d, 70.0d,
                    interpolate(logOverpressure, SovietOverpressureData.R_GROUND),
                    interpolate(logOverpressure, SovietOverpressureData.R_NOMACH_SH7));
        }
        throw new ValueOutsideGraphException(scaleHeight);
    }

    /**
     * 在苏联“有热前驱层”图表中反查距离。
     */
    private static double inverseSovietMach(double scaleHeight, double overpressureKgPerCm2) {
        double logOverpressure = FastMath.log10(overpressureKgPerCm2);
        if (scaleHeight >= 120.0d && overpressureKgPerCm2 > 2.2336d) {
            return inverseHighOverpressure(scaleHeight, logOverpressure, false, 18);
        }
        if (scaleHeight >= 120.0d && scaleHeight <= 200.0d) {
            return Interpolation.linear(scaleHeight, 120.0d, 200.0d,
                    interpolate(logOverpressure, SovietOverpressureData.R_MACH_SH12),
                    interpolate(logOverpressure, SovietOverpressureData.R_MACH_SH20));
        }
        if (scaleHeight >= 70.0d && scaleHeight < 120.0d) {
            return Interpolation.linear(scaleHeight, 70.0d, 120.0d,
                    interpolate(logOverpressure, SovietOverpressureData.R_MACH_SH7),
                    interpolate(logOverpressure, SovietOverpressureData.R_MACH_SH12));
        }
        if (scaleHeight >= 0.0d && scaleHeight < 70.0d) {
            return Interpolation.linear(scaleHeight, 0.0d, 70.0d,
                    interpolate(logOverpressure, SovietOverpressureData.R_GROUND),
                    interpolate(logOverpressure, SovietOverpressureData.R_MACH_SH7));
        }
        throw new ValueOutsideGraphException(scaleHeight);
    }

    /**
     * 对高超压段使用离散采样加插值进行反查。
     */
    private static double inverseHighOverpressure(double scaleHeight, double logOverpressure, boolean thermalLayer, int count) {
        double[] distances = new double[count];
        double[] logs = new double[count];
        for (int index = 0; index < count; index++) {
            distances[index] = (count - index - 1) * 10.0d;
            double overpressure = thermalLayer
                    ? sovietNoMach(scaleHeight, distances[index])
                    : sovietMach(scaleHeight, distances[index]);
            logs[index] = FastMath.log10(overpressure);
        }
        return Interpolation.interpolate(logOverpressure, logs, distances);
    }

    /**
     * 在苏联数据序列上做插值。
     */
    private static double interpolate(double value, SovietOverpressureData.Series series) {
        return Interpolation.interpolate(value, series.xs(), series.ys());
    }

    /**
     * 先线性插值对数值，再还原到线性域。
     */
    private static double lerp10(double h, double h1, double h2, double o1, double o2) {
        return FastMath.pow(10.0d, Interpolation.linear(h, h1, h2, o1, o2));
    }

    private static double scaledRange(double yieldKt, double range) {
        return range / FastMath.cbrt(yieldKt);
    }

    private static double scaledHeight(double yieldKt, double burstHeight) {
        return burstHeight / FastMath.cbrt(yieldKt);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return FastMath.max(minimum, FastMath.min(maximum, value));
    }

    private static double inverseOverpressureRangeMeters(
            double targetOverpressure,
            DoubleUnaryOperator overpressureAtRangeMeters,
            String modelName
    ) {
        List<Double> ranges = inverseOverpressureRangesMeters(
                targetOverpressure,
                overpressureAtRangeMeters,
                modelName,
                RANGE_SCAN_STEPS
        );
        return ranges.get(0);
    }

    private static double inverseOverpressureRangeMeters(
            double targetOverpressure,
            DoubleUnaryOperator overpressureAtRangeMeters,
            String modelName,
            int rangeScanSteps
    ) {
        List<Double> ranges = inverseOverpressureRangesMeters(
                targetOverpressure,
                overpressureAtRangeMeters,
                modelName,
                rangeScanSteps
        );
        return ranges.get(0);
    }

    private static List<Double> inverseOverpressureRangesMeters(
            double targetOverpressure,
            DoubleUnaryOperator overpressureAtRangeMeters,
            String modelName
    ) {
        return inverseOverpressureRangesMeters(
                targetOverpressure,
                overpressureAtRangeMeters,
                modelName,
                RANGE_SCAN_STEPS
        );
    }

    private static List<Double> inverseOverpressureRangesMeters(
            double targetOverpressure,
            DoubleUnaryOperator overpressureAtRangeMeters,
            String modelName,
            int rangeScanSteps
    ) {
        List<RangeBracket> brackets = findRangeBrackets(targetOverpressure, overpressureAtRangeMeters, rangeScanSteps);
        if (brackets.isEmpty()) {
            throw new IllegalArgumentException(modelName + " overpressure target is outside the solvable range");
        }

        List<Double> roots = new ArrayList<>(brackets.size());
        for (RangeBracket bracket : brackets) {
            roots.add(solveBracket(bracket, targetOverpressure, overpressureAtRangeMeters));
        }
        return List.copyOf(roots);
    }

    private static double solveBracket(
            RangeBracket bracket,
            double targetOverpressure,
            DoubleUnaryOperator overpressureAtRangeMeters
    ) {
        if (bracket.lowerMeters() == bracket.upperMeters()) {
            return bracket.lowerMeters();
        }
        double lower = bracket.lowerMeters();
        double upper = bracket.upperMeters();
        double lowerDifference = overpressureAtRangeMeters.applyAsDouble(lower) - targetOverpressure;
        for (int iteration = 0; iteration < RANGE_BISECTION_STEPS; iteration++) {
            double midpoint = (lower + upper) * 0.5d;
            double midpointDifference = overpressureAtRangeMeters.applyAsDouble(midpoint) - targetOverpressure;
            if (midpointDifference == 0.0d) {
                return midpoint;
            }
            if (sameSign(lowerDifference, midpointDifference)) {
                lower = midpoint;
                lowerDifference = midpointDifference;
            } else {
                upper = midpoint;
            }
        }
        return (lower + upper) * 0.5d;
    }

    private static List<RangeBracket> findRangeBrackets(
            double targetOverpressure,
            DoubleUnaryOperator overpressureAtRangeMeters
    ) {
        return findRangeBrackets(targetOverpressure, overpressureAtRangeMeters, RANGE_SCAN_STEPS);
    }

    private static List<RangeBracket> findRangeBrackets(
            double targetOverpressure,
            DoubleUnaryOperator overpressureAtRangeMeters,
            int rangeScanSteps
    ) {
        double growthFactor = FastMath.pow(MAX_SOLVER_RANGE_METERS / MIN_SOLVER_RANGE_METERS, 1.0d / rangeScanSteps);
        double previousRange = MIN_SOLVER_RANGE_METERS;
        double previousDifference = overpressureAtRangeMeters.applyAsDouble(previousRange) - targetOverpressure;
        List<RangeBracket> brackets = new ArrayList<>();
        if (previousDifference == 0.0d) {
            brackets.add(new RangeBracket(previousRange, previousRange));
        }

        for (int step = 1; step <= rangeScanSteps; step++) {
            double currentRange = previousRange * growthFactor;
            double currentDifference = overpressureAtRangeMeters.applyAsDouble(currentRange) - targetOverpressure;
            if (currentDifference == 0.0d) {
                brackets.add(new RangeBracket(currentRange, currentRange));
            } else if (previousDifference != 0.0d && !sameSign(previousDifference, currentDifference)) {
                brackets.add(new RangeBracket(previousRange, currentRange));
            }
            previousRange = currentRange;
            previousDifference = currentDifference;
        }
        return List.copyOf(brackets);
    }

    private static boolean sameSign(double left, double right) {
        return (left > 0.0d && right > 0.0d) || (left < 0.0d && right < 0.0d);
    }

    private static int rangeScanStepsFromSampleCount(int n) {
        if (n < 4) {
            throw new IllegalArgumentException("n must be greater than or equal to 4");
        }
        if (n > Integer.MAX_VALUE / 4) {
            throw new IllegalArgumentException("n is too large");
        }
        return Math.max(n * 4, 200);
    }

    private static void requirePositiveOverpressure(double overpressure) {
        if (!(overpressure > 0.0d) || Double.isNaN(overpressure) || Double.isInfinite(overpressure)) {
            throw new IllegalArgumentException("overpressure must be positive");
        }
    }

    private static void requirePositiveRange(double range) {
        if (!(range > 0.0d) || Double.isNaN(range) || Double.isInfinite(range)) {
            throw new IllegalArgumentException("range must be positive");
        }
    }

    private record RangeBracket(double lowerMeters, double upperMeters) {
    }
}
