package com.glasstone.radiation;

import com.glasstone.exception.ValueOutsideGraphException;
import com.glasstone.math.Interpolation;
import com.glasstone.model.BurstParameters;
import com.glasstone.units.DistanceUnit;
import com.glasstone.units.DoseUnit;
import com.glasstone.units.UnitConverter;
import com.glasstone.units.YieldUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import org.hipparchus.util.FastMath;

/**
 * 电离辐射计算器。
 * <p>
 * 同时提供苏联经验图表与《The Effects of Nuclear Weapons / Glasstone》体系下的剂量模型。
 */
public final class RadiationCalculator {
    private static final double EFFECTIVE_YIELD_RELATIVE_TOLERANCE = 5.0e-2d;
    private static final int RANGE_SCAN_STEPS = 512;
    private static final int RANGE_BISECTION_STEPS = 80;

    /**
     * 热核武器二次伽马计算使用的固定裂变份额。
     */
    private static final double THERMONUCLEAR_FISSION_FRACTION = 0.5d;

    private RadiationCalculator() {
    }

    /**
     * 计算苏联模型下的总穿透辐射剂量。
     */
    public static double sovietSummary(
            double yield,
            double slantRange,
            DistanceUnit distanceUnit,
            RadiationScenario scenario,
            DoseUnit outputUnit
    ) {
        double rangeMeters = UnitConverter.convertDistance(slantRange, distanceUnit, DistanceUnit.METERS);
        // factor: 季节或地形修正系数。
        double factor = switch (scenario) {
            case SUMMER -> 1.0d;
            case WINTER -> gammaWinterAdjust(rangeMeters);
            case MOUNTAIN -> gammaMountainAdjust(rangeMeters);
        };
        double dose = interpolateSoviet(yield, rangeMeters, SovietRadiationData.SUMMARY_X, SovietRadiationData.SUMMARY_Y) * factor;
        return UnitConverter.convertDose(dose, DoseUnit.ROENTGEN, outputUnit);
    }

    /**
     * 计算苏联模型下的伽马剂量。
     */
    public static double sovietGamma(
            double yield,
            double slantRange,
            DistanceUnit distanceUnit,
            RadiationScenario scenario,
            DoseUnit outputUnit
    ) {
        double rangeMeters = UnitConverter.convertDistance(slantRange, distanceUnit, DistanceUnit.METERS);
        double factor = switch (scenario) {
            case SUMMER -> 1.0d;
            case WINTER -> gammaWinterAdjust(rangeMeters);
            case MOUNTAIN -> gammaMountainAdjust(rangeMeters);
        };
        double dose = interpolateSoviet(yield, rangeMeters, SovietRadiationData.GAMMA_X, SovietRadiationData.GAMMA_Y) * factor;
        return UnitConverter.convertDose(dose, DoseUnit.ROENTGEN, outputUnit);
    }

    /**
     * 计算苏联模型下的中子剂量。
     */
    public static double sovietNeutron(
            double yield,
            double slantRange,
            DistanceUnit distanceUnit,
            RadiationScenario scenario,
            DoseUnit outputUnit
    ) {
        double rangeMeters = UnitConverter.convertDistance(slantRange, distanceUnit, DistanceUnit.METERS);
        double factor = switch (scenario) {
            case SUMMER -> 1.0d;
            case WINTER -> neutronWinterAdjust(rangeMeters);
            case MOUNTAIN -> neutronMountainAdjust(rangeMeters);
        };
        double dose = interpolateSoviet(yield, rangeMeters, SovietRadiationData.NEUTRON_X, SovietRadiationData.NEUTRON_Y) * factor;
        return UnitConverter.convertDose(dose, DoseUnit.ROENTGEN, outputUnit);
    }

    /**
     * 计算 Glasstone 体系下的总穿透辐射剂量。
     */
    public static double glasstoneSummary(
            BurstParameters burst,
            double slantRange,
            DistanceUnit distanceUnit,
            double fissionFraction,
            boolean offense,
            boolean surface,
            DoseUnit outputUnit
    ) {
        requireFraction(fissionFraction);
        double yieldKt = yieldKiltons(burst);
        double slantRangeYards = slantRangeYards(slantRange, distanceUnit);
        double burstHeightFeet = burstHeightFeet(burst);

        // neutron / secondaryGamma / fissionFragmentGamma: 三个主剂量分量。
        double neutron = isFissionWeapon(fissionFraction)
                ? glasstoneFissionNeutronDose(yieldKt, slantRangeYards, burstHeightFeet, offense)
                : glasstoneThermonuclearNeutronDose(yieldKt, slantRangeYards, burstHeightFeet);
        double secondaryGamma = isFissionWeapon(fissionFraction)
                ? glasstoneFissionSecondaryGammaDose(yieldKt, slantRangeYards, burstHeightFeet, offense)
                : glasstoneThermonuclearSecondaryGammaDose(yieldKt, slantRangeYards, burstHeightFeet);
        double fissionFragmentGamma = glasstoneFissionFragmentGammaDose(yieldKt, slantRangeYards, fissionFraction, surface);
        double totalDose = neutron + secondaryGamma + fissionFragmentGamma;
        return UnitConverter.convertDose(totalDose, DoseUnit.ROENTGEN, outputUnit);
    }

    /**
     * 根据 Glasstone 总穿透辐射剂量反推斜距，默认以米返回。
     * <p>
     * 对于地爆可传入爆高为 0 的 {@link BurstParameters}，并将 {@code surface} 设为 {@code true}。
     */
    public static double inverseGlasstoneSummary(
            BurstParameters burst,
            double dose,
            DoseUnit doseUnit,
            double fissionFraction,
            boolean offense,
            boolean surface
    ) {
        return inverseGlasstoneSummary(
                burst,
                dose,
                doseUnit,
                fissionFraction,
                offense,
                surface,
                DistanceUnit.METERS
        );
    }

    /**
     * 根据 Glasstone 总穿透辐射剂量反推斜距。
     * <p>
     * 若局部存在多个交点，默认返回外侧距离，便于按等剂量环绘制。
     */
    public static double inverseGlasstoneSummary(
            BurstParameters burst,
            double dose,
            DoseUnit doseUnit,
            double fissionFraction,
            boolean offense,
            boolean surface,
            DistanceUnit outputUnit
    ) {
        requirePositive(dose);
        requireFraction(fissionFraction);
        double targetDoseRoentgen = UnitConverter.convertDose(dose, doseUnit, DoseUnit.ROENTGEN);
        RangeBounds domain = glasstoneSummaryDomain(fissionFraction, offense);
        double slantRangeYards = inverseGlasstoneRangeYards(
                targetDoseRoentgen,
                dose,
                domain,
                candidateRangeYards -> glasstoneSummary(
                        burst,
                        candidateRangeYards,
                        DistanceUnit.YARDS,
                        fissionFraction,
                        offense,
                        surface,
                        DoseUnit.ROENTGEN
                )
        );
        return UnitConverter.convertDistance(slantRangeYards, DistanceUnit.YARDS, outputUnit);
    }

    /**
     * 计算 Glasstone 体系下的裂变碎片伽马剂量。
     */
    public static double glasstoneFissionFragmentGamma(
            BurstParameters burst,
            double slantRange,
            DistanceUnit distanceUnit,
            double fissionFraction,
            boolean surface,
            DoseUnit outputUnit
    ) {
        requireFraction(fissionFraction);
        double yieldKt = yieldKiltons(burst);
        double slantRangeYards = slantRangeYards(slantRange, distanceUnit);
        double dose = glasstoneFissionFragmentGammaDose(yieldKt, slantRangeYards, fissionFraction, surface);
        return UnitConverter.convertDose(dose, DoseUnit.ROENTGEN, outputUnit);
    }

    /**
     * 计算 Glasstone 体系下的裂变二次伽马剂量。
     */
    public static double glasstoneFissionSecondaryGamma(
            BurstParameters burst,
            double slantRange,
            DistanceUnit distanceUnit,
            boolean offense,
            DoseUnit outputUnit
    ) {
        double dose = glasstoneFissionSecondaryGammaDose(
                yieldKiltons(burst),
                slantRangeYards(slantRange, distanceUnit),
                burstHeightFeet(burst),
                offense
        );
        return UnitConverter.convertDose(dose, DoseUnit.ROENTGEN, outputUnit);
    }

    /**
     * 计算 Glasstone 体系下的热核二次伽马剂量。
     */
    public static double glasstoneThermonuclearSecondaryGamma(
            BurstParameters burst,
            double slantRange,
            DistanceUnit distanceUnit,
            DoseUnit outputUnit
    ) {
        double dose = glasstoneThermonuclearSecondaryGammaDose(
                yieldKiltons(burst),
                slantRangeYards(slantRange, distanceUnit),
                burstHeightFeet(burst)
        );
        return UnitConverter.convertDose(dose, DoseUnit.ROENTGEN, outputUnit);
    }

    /**
     * 计算 Glasstone 体系下的裂变中子剂量。
     */
    public static double glasstoneFissionNeutron(
            BurstParameters burst,
            double slantRange,
            DistanceUnit distanceUnit,
            boolean offense,
            DoseUnit outputUnit
    ) {
        double dose = glasstoneFissionNeutronDose(
                yieldKiltons(burst),
                slantRangeYards(slantRange, distanceUnit),
                burstHeightFeet(burst),
                offense
        );
        return UnitConverter.convertDose(dose, DoseUnit.ROENTGEN, outputUnit);
    }

    /**
     * 计算 Glasstone 体系下的热核中子剂量。
     */
    public static double glasstoneThermonuclearNeutron(
            BurstParameters burst,
            double slantRange,
            DistanceUnit distanceUnit,
            DoseUnit outputUnit
    ) {
        double dose = glasstoneThermonuclearNeutronDose(
                yieldKiltons(burst),
                slantRangeYards(slantRange, distanceUnit),
                burstHeightFeet(burst)
        );
        return UnitConverter.convertDose(dose, DoseUnit.ROENTGEN, outputUnit);
    }

    /**
     * 在苏联图表数据上做“两层插值”：先按距离插值，再按当量插值。
     */
    private static double interpolateSoviet(double yield, double scaleRange, double[][] xsTable, double[][] ysTable) {
        requirePositive(yield);
        requirePositive(scaleRange);

        double[] yields = SovietRadiationData.YIELDS;
        validateYieldRange(yield, yields[0], yields[yields.length - 1]);

        for (int index = 0; index < yields.length - 1; index++) {
            double lowerYield = yields[index];
            double upperYield = yields[index + 1];
            if (yield >= lowerYield && yield <= upperYield) {
                // lower/upper: 当量上下边界对应的两组图表曲线。
                double[] lowerXs = xsTable[index];
                double[] lowerYs = ysTable[index];
                double[] upperXs = xsTable[index + 1];
                double[] upperYs = ysTable[index + 1];

                validateRange(scaleRange, lowerXs[0], lowerXs[lowerXs.length - 1]);
                validateRange(scaleRange, upperXs[0], upperXs[upperXs.length - 1]);

                double lowerValue = Interpolation.interpolate(scaleRange, lowerXs, lowerYs);
                double upperValue = Interpolation.interpolate(scaleRange, upperXs, upperYs);
                return FastMath.pow(10.0d, Interpolation.linear(yield, lowerYield, upperYield, lowerValue, upperValue));
            }
        }

        throw new ValueOutsideGraphException(yield);
    }

    private static double neutronMountainAdjust(double scaleRange) {
        return FastMath.pow(10.0d, 2.715065E-4d * scaleRange);
    }

    private static double neutronWinterAdjust(double scaleRange) {
        return FastMath.pow(10.0d, -5.0064374E-4d * scaleRange);
    }

    private static double gammaMountainAdjust(double scaleRange) {
        return FastMath.pow(10.0d, 1.6580746E-4d * scaleRange);
    }

    private static double gammaWinterAdjust(double scaleRange) {
        return FastMath.pow(10.0d, -3.2679923E-4d * scaleRange);
    }

    /**
     * 计算 Glasstone 裂变二次伽马剂量。
     */
    private static double glasstoneFissionSecondaryGammaDose(
            double yieldKt,
            double slantRangeYards,
            double burstHeightFeet,
            boolean offense
    ) {
        GlasstoneRadiationData.Series series = offense
                ? GlasstoneRadiationData.FISSION_SECONDARY_GAMMA_OFFENSE
                : GlasstoneRadiationData.FISSION_SECONDARY_GAMMA_DEFENSE;
        double airburstDose = interpolateGlasstone(series, slantRangeYards) * yieldKt;
        return interpolateHeightDose(burstHeightFeet, airburstDose);
    }

    /**
     * 计算 Glasstone 热核二次伽马剂量。
     */
    private static double glasstoneThermonuclearSecondaryGammaDose(
            double yieldKt,
            double slantRangeYards,
            double burstHeightFeet
    ) {
        double airburstDose = interpolateGlasstone(GlasstoneRadiationData.THERMONUCLEAR_SECONDARY_GAMMA, slantRangeYards)
                * yieldKt
                * THERMONUCLEAR_FISSION_FRACTION;
        return interpolateHeightDose(burstHeightFeet, airburstDose);
    }

    /**
     * 计算 Glasstone 裂变中子剂量。
     */
    private static double glasstoneFissionNeutronDose(
            double yieldKt,
            double slantRangeYards,
            double burstHeightFeet,
            boolean offense
    ) {
        GlasstoneRadiationData.Series series = offense
                ? GlasstoneRadiationData.FISSION_NEUTRON_OFFENSE
                : GlasstoneRadiationData.FISSION_NEUTRON_DEFENSE;
        double airburstDose = interpolateGlasstone(series, slantRangeYards) * yieldKt;
        return interpolateHeightDose(burstHeightFeet, airburstDose);
    }

    /**
     * 计算 Glasstone 热核中子剂量。
     */
    private static double glasstoneThermonuclearNeutronDose(
            double yieldKt,
            double slantRangeYards,
            double burstHeightFeet
    ) {
        double airburstDose = interpolateGlasstone(GlasstoneRadiationData.THERMONUCLEAR_NEUTRON, slantRangeYards) * yieldKt;
        return interpolateHeightDose(burstHeightFeet, airburstDose);
    }

    /**
     * 计算 Glasstone 裂变碎片伽马剂量。
     */
    private static double glasstoneFissionFragmentGammaDose(
            double yieldKt,
            double slantRangeYards,
            double fissionFraction,
            boolean surface
    ) {
        double oneKilotonDose = interpolateGlasstone(GlasstoneRadiationData.FFGAMMA, slantRangeYards);
        double effectiveYield = surface
                ? effectiveYield(yieldKt, slantRangeYards, GlasstoneRadiationData.SURFACE_EFF_X, GlasstoneRadiationData.SURFACE_EFF_Y)
                : effectiveYield(yieldKt, slantRangeYards, GlasstoneRadiationData.AIRBURST_EFF_X, GlasstoneRadiationData.AIRBURST_EFF_Y);
        return effectiveYield * oneKilotonDose * fissionFraction;
    }

    /**
     * 根据图表查找“有效当量”，用于裂变碎片伽马修正。
     */
    private static double effectiveYield(
            double yieldKt,
            double slantRangeYards,
            double[][] xsTable,
            double[][] ysTable
    ) {
        int[] ranges = GlasstoneRadiationData.EFF_YIELD_RANGES;
        int lowerIndex = lowerBoundIndex(slantRangeYards, ranges);
        int upperIndex = upperBoundIndex(slantRangeYards, ranges, lowerIndex);
        double[] lowerXs = xsTable[lowerIndex];
        double[] upperXs = xsTable[upperIndex];
        double lowerBound = FastMath.max(lowerXs[0], upperXs[0]);
        double upperBound = FastMath.min(lowerXs[lowerXs.length - 1], upperXs[upperXs.length - 1]);
        double boundedLogYield = boundedLogYield(yieldKt, lowerBound, upperBound);
        double lowerValue = Interpolation.interpolate(boundedLogYield, lowerXs, ysTable[lowerIndex]);
        if (lowerIndex == upperIndex) {
            return FastMath.pow(10.0d, lowerValue);
        }
        double upperValue = Interpolation.interpolate(boundedLogYield, upperXs, ysTable[upperIndex]);
        return FastMath.pow(10.0d, Interpolation.linear(
                slantRangeYards,
                ranges[lowerIndex],
                ranges[upperIndex],
                lowerValue,
                upperValue
        ));
    }

    private static double boundedLogYield(double yieldKt, double lowerBound, double upperBound) {
        double lowerYield = FastMath.pow(10.0d, lowerBound);
        double upperYield = FastMath.pow(10.0d, upperBound);
        if (yieldKt < lowerYield && !isCloseWithinRelativeTolerance(yieldKt, lowerYield, EFFECTIVE_YIELD_RELATIVE_TOLERANCE)) {
            throw new ValueOutsideGraphException(yieldKt);
        }
        if (yieldKt > upperYield && !isCloseWithinRelativeTolerance(yieldKt, upperYield, EFFECTIVE_YIELD_RELATIVE_TOLERANCE)) {
            throw new ValueOutsideGraphException(yieldKt);
        }
        return FastMath.max(lowerBound, FastMath.min(upperBound, FastMath.log10(yieldKt)));
    }

    private static boolean isCloseWithinRelativeTolerance(double value, double reference, double relativeTolerance) {
        return FastMath.abs(value - reference) <= relativeTolerance * FastMath.abs(reference);
    }

    /**
     * 在 Glasstone 曲线上读取剂量值。
     */
    private static double interpolateGlasstone(GlasstoneRadiationData.Series series, double slantRangeYards) {
        requirePositive(slantRangeYards);
        validateRange(slantRangeYards, series.xs()[0], series.xs()[series.xs().length - 1]);
        return FastMath.pow(10.0d, Interpolation.interpolate(slantRangeYards, series.xs(), series.ys()));
    }

    /**
     * 按爆高对地表/空爆剂量做线性修正。
     */
    private static double interpolateHeightDose(double burstHeightFeet, double airburstDose) {
        if (burstHeightFeet <= 0.0d) {
            return airburstDose * 0.5d;
        }
        if (burstHeightFeet >= 300.0d) {
            return airburstDose;
        }
        return Interpolation.linear(burstHeightFeet, 0.0d, 300.0d, airburstDose * 0.5d, airburstDose);
    }

    /**
     * 判断当前裂变份额是否应使用“裂变武器”分支。
     */
    private static boolean isFissionWeapon(double fissionFraction) {
        return fissionFraction >= 0.8d && fissionFraction <= 1.0d;
    }

    private static RangeBounds glasstoneSummaryDomain(double fissionFraction, boolean offense) {
        boolean fissionWeapon = isFissionWeapon(fissionFraction);
        GlasstoneRadiationData.Series secondaryGammaSeries = secondaryGammaSeries(fissionWeapon, offense);
        GlasstoneRadiationData.Series neutronSeries = neutronSeries(fissionWeapon, offense);
        RangeBounds fragmentGammaBounds = new RangeBounds(
                FastMath.max(
                        GlasstoneRadiationData.FFGAMMA.xs()[0],
                        GlasstoneRadiationData.EFF_YIELD_RANGES[0]
                ),
                FastMath.min(
                        GlasstoneRadiationData.FFGAMMA.xs()[GlasstoneRadiationData.FFGAMMA.xs().length - 1],
                        GlasstoneRadiationData.EFF_YIELD_RANGES[GlasstoneRadiationData.EFF_YIELD_RANGES.length - 1]
                )
        );
        return intersectBounds(
                intersectBounds(seriesBounds(secondaryGammaSeries), seriesBounds(neutronSeries)),
                fragmentGammaBounds
        );
    }

    private static GlasstoneRadiationData.Series secondaryGammaSeries(boolean fissionWeapon, boolean offense) {
        if (!fissionWeapon) {
            return GlasstoneRadiationData.THERMONUCLEAR_SECONDARY_GAMMA;
        }
        return offense
                ? GlasstoneRadiationData.FISSION_SECONDARY_GAMMA_OFFENSE
                : GlasstoneRadiationData.FISSION_SECONDARY_GAMMA_DEFENSE;
    }

    private static GlasstoneRadiationData.Series neutronSeries(boolean fissionWeapon, boolean offense) {
        if (!fissionWeapon) {
            return GlasstoneRadiationData.THERMONUCLEAR_NEUTRON;
        }
        return offense
                ? GlasstoneRadiationData.FISSION_NEUTRON_OFFENSE
                : GlasstoneRadiationData.FISSION_NEUTRON_DEFENSE;
    }

    private static double yieldKiltons(BurstParameters burst) {
        return UnitConverter.convertYield(burst.yield(), burst.yieldUnit(), YieldUnit.KILOTONS);
    }

    private static double slantRangeYards(double slantRange, DistanceUnit distanceUnit) {
        return UnitConverter.convertDistance(slantRange, distanceUnit, DistanceUnit.YARDS);
    }

    private static double burstHeightFeet(BurstParameters burst) {
        return UnitConverter.convertDistance(burst.burstHeight(), burst.distanceUnit(), DistanceUnit.FEET);
    }

    private static RangeBounds seriesBounds(GlasstoneRadiationData.Series series) {
        return new RangeBounds(series.xs()[0], series.xs()[series.xs().length - 1]);
    }

    private static RangeBounds intersectBounds(RangeBounds left, RangeBounds right) {
        double lower = FastMath.max(left.lowerBound(), right.lowerBound());
        double upper = FastMath.min(left.upperBound(), right.upperBound());
        if (lower > upper) {
            throw new IllegalStateException("Glasstone summary dose domain is empty");
        }
        return new RangeBounds(lower, upper);
    }

    private static double inverseGlasstoneRangeYards(
            double targetDoseRoentgen,
            double sourceDose,
            RangeBounds domain,
            DoubleUnaryOperator doseAtRangeYards
    ) {
        List<RangeBracket> brackets = findDoseBrackets(targetDoseRoentgen, domain, doseAtRangeYards);
        if (brackets.isEmpty()) {
            throw new ValueOutsideGraphException(sourceDose);
        }
        return solveDoseBracket(brackets.get(brackets.size() - 1), targetDoseRoentgen, doseAtRangeYards);
    }

    private static List<RangeBracket> findDoseBrackets(
            double targetDoseRoentgen,
            RangeBounds domain,
            DoubleUnaryOperator doseAtRangeYards
    ) {
        double lowerBound = domain.lowerBound();
        double upperBound = domain.upperBound();
        double growthFactor = FastMath.pow(upperBound / lowerBound, 1.0d / RANGE_SCAN_STEPS);
        double previousRange = lowerBound;
        double previousDifference = doseAtRangeYards.applyAsDouble(previousRange) - targetDoseRoentgen;
        List<RangeBracket> brackets = new ArrayList<>();
        if (previousDifference == 0.0d) {
            brackets.add(new RangeBracket(previousRange, previousRange));
        }

        for (int step = 1; step <= RANGE_SCAN_STEPS; step++) {
            double currentRange = step == RANGE_SCAN_STEPS ? upperBound : lowerBound * FastMath.pow(growthFactor, step);
            double currentDifference = doseAtRangeYards.applyAsDouble(currentRange) - targetDoseRoentgen;
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

    private static double solveDoseBracket(
            RangeBracket bracket,
            double targetDoseRoentgen,
            DoubleUnaryOperator doseAtRangeYards
    ) {
        if (bracket.lowerBound() == bracket.upperBound()) {
            return bracket.lowerBound();
        }
        double lower = bracket.lowerBound();
        double upper = bracket.upperBound();
        double lowerDifference = doseAtRangeYards.applyAsDouble(lower) - targetDoseRoentgen;
        for (int iteration = 0; iteration < RANGE_BISECTION_STEPS; iteration++) {
            double midpoint = (lower + upper) * 0.5d;
            double midpointDifference = doseAtRangeYards.applyAsDouble(midpoint) - targetDoseRoentgen;
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

    private static boolean sameSign(double left, double right) {
        return (left > 0.0d && right > 0.0d) || (left < 0.0d && right < 0.0d);
    }

    private static int lowerBoundIndex(double slantRange, int[] ranges) {
        validateRange(slantRange, ranges[0], ranges[ranges.length - 1]);
        // index: 落在 slantRange 左侧的最近档位。
        int index = 0;
        while (index < ranges.length - 1 && slantRange >= ranges[index + 1]) {
            index++;
        }
        return index;
    }

    private static int upperBoundIndex(double slantRange, int[] ranges, int lowerIndex) {
        return lowerIndex == ranges.length - 1 ? lowerIndex : lowerIndex + 1;
    }

    private static void requirePositive(double value) {
        if (!(value > 0.0d) || Double.isNaN(value) || Double.isInfinite(value)) {
            throw new ValueOutsideGraphException(value);
        }
    }

    private static void requireFraction(double value) {
        if (!Double.isFinite(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException("fissionFraction must be between 0 and 1");
        }
    }

    private static void validateYieldRange(double value, double minimum, double maximum) {
        if (value < minimum || value > maximum) {
            throw new ValueOutsideGraphException(value);
        }
    }

    private static void validateRange(double value, double minimum, double maximum) {
        if (value < minimum || value > maximum) {
            throw new ValueOutsideGraphException(value);
        }
    }

    private record RangeBounds(double lowerBound, double upperBound) {
    }

    private record RangeBracket(double lowerBound, double upperBound) {
    }
}
