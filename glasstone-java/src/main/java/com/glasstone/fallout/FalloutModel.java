package com.glasstone.fallout;

import com.glasstone.math.SpecialFunctions;
import com.glasstone.model.Coordinate2D;
import com.glasstone.model.WindProfile;
import com.glasstone.units.DistanceUnit;
import com.glasstone.units.DoseUnit;
import com.glasstone.units.SpeedUnit;
import com.glasstone.units.UnitConverter;
import com.glasstone.units.WindShearUnit;
import com.glasstone.units.YieldUnit;
import org.hipparchus.util.FastMath;

/**
 * WSEG-10 放射性沉降模型。
 * <p>
 * 该类在构造阶段完成与云团形状、风场和扩散参数相关的预计算，
 * 随后可以重复查询任意坐标点的到达时间、H+1 剂量率和等效残余剂量。
 */
public final class FalloutModel {
    private static final int DEFAULT_CONTOUR_POINT_COUNT = 100;
    private static final int MIN_CONTOUR_POINT_COUNT = 4;
    private static final int CONTOUR_SAMPLE_COUNT = 2_049;
    private static final int CONTOUR_EXPANSION_LIMIT = 32;
    private static final int CONTOUR_BISECTION_STEPS = 80;

    /**
     * 正态分布密度函数中的常数项 `sqrt(2π)`。
     */
    private static final double SQRT_TWO_PI = FastMath.sqrt(2.0d * FastMath.PI);

    /**
     * 原始配置对象，便于在必要时回溯输入参数。
     */
    private final FalloutConfig config;
    /**
     * 爆心横坐标，统一换算为英里。
     */
    private final double groundZeroMilesX;
    /**
     * 爆心纵坐标，统一换算为英里。
     */
    private final double groundZeroMilesY;
    /**
     * 爆炸当量，统一换算为兆吨。
     */
    private final double yieldMegatons;
    /**
     * 风速，统一换算为英里每小时。
     */
    private final double windMilesPerHour;
    /**
     * 风切变，统一换算为英里每小时每千英尺。
     */
    private final double shearMilesPerHourPerKilofoot;
    /**
     * 坐标旋转所需的余弦值。
     */
    private final double rotationCos;
    /**
     * 坐标旋转所需的正弦值。
     */
    private final double rotationSin;
    /**
     * 云团中心高度参数。
     */
    private final double cloudCenterHeight;
    /**
     * 初始横向扩散尺度参数。
     */
    private final double sigma0;
    /**
     * `sigma0` 的平方，供公式复用。
     */
    private final double sigma0Squared;
    /**
     * 云团高度尺度参数。
     */
    private final double sigmaH;
    /**
     * 沉降时间常数。
     */
    private final double timeConstant;
    /**
     * 顺风方向参考尺度 `L0`。
     */
    private final double l0;
    /**
     * `L0` 的平方，供公式复用。
     */
    private final double l0Squared;
    /**
     * 顺风扩散方差。
     */
    private final double sigmaXSquared;
    /**
     * 顺风扩散标准差。
     */
    private final double sigmaX;
    /**
     * 主尺度 `L` 的平方。
     */
    private final double lSquared;
    /**
     * 主尺度 `L`。
     */
    private final double l;
    /**
     * 顺风分布函数的指数参数。
     */
    private final double n;
    /**
     * 顺风累积分布修正系数。
     */
    private final double alpha1;

    /**
     * 根据输入配置初始化沉降模型。
     *
     * @param config WSEG-10 输入配置
     */
    public FalloutModel(FalloutConfig config) {
        this.config = config;

        // 基础输入：爆心位置与风场参数。
        Coordinate2D groundZero = config.groundZero();
        WindProfile windProfile = config.windProfile();
        this.groundZeroMilesX = UnitConverter.convertDistance(groundZero.x(), config.distanceUnit(), DistanceUnit.MILES);
        this.groundZeroMilesY = UnitConverter.convertDistance(groundZero.y(), config.distanceUnit(), DistanceUnit.MILES);
        this.yieldMegatons = UnitConverter.convertYield(config.yield(), config.yieldUnit(), YieldUnit.MEGATONS);
        this.windMilesPerHour = UnitConverter.convertSpeed(windProfile.speed(), windProfile.speedUnit(), SpeedUnit.MILES_PER_HOUR);
        this.shearMilesPerHourPerKilofoot = UnitConverter.convertWindShear(
                windProfile.shear(),
                windProfile.shearUnit(),
                WindShearUnit.MILES_PER_HOUR_PER_KILOFOOT);

        // 坐标旋转：把地图坐标转为顺风/横风坐标系。
        double rotationRadians = FastMath.toRadians(windProfile.directionDegrees() - 270.0d);
        this.rotationCos = FastMath.cos(rotationRadians);
        this.rotationSin = FastMath.sin(rotationRadians);

        // 云团几何：云心高度与初始横向扩散尺度。
        double d = FastMath.log(this.yieldMegatons) + 2.42d;
        this.cloudCenterHeight = 44.0d + (6.1d * FastMath.log(this.yieldMegatons)) - (0.205d * FastMath.abs(d) * d);

        double logYield = FastMath.log(this.yieldMegatons);
        this.sigma0 = FastMath.exp(0.7d + (logYield / 3.0d) - (3.25d / (4.0d + FastMath.pow(logYield + 5.4d, 2.0d))));
        this.sigma0Squared = sigma0 * sigma0;
        this.sigmaH = 0.18d * cloudCenterHeight;

        // 平流与扩散：timeConstant、l0、sigmaX、l 是 WSEG-10 的核心尺度参数。
        this.timeConstant = 1.0573203d
                * ((12.0d * (cloudCenterHeight / 60.0d)) - (2.5d * FastMath.pow(cloudCenterHeight / 60.0d, 2.0d)))
                * (1.0d - (0.5d * FastMath.exp(-FastMath.pow(cloudCenterHeight / 25.0d, 2.0d))));
        this.l0 = windMilesPerHour * timeConstant;
        this.l0Squared = l0 * l0;
        this.sigmaXSquared = sigma0Squared * (l0Squared + (8.0d * sigma0Squared)) / (l0Squared + (2.0d * sigma0Squared));
        this.sigmaX = FastMath.sqrt(sigmaXSquared);
        this.lSquared = l0Squared + (2.0d * sigmaXSquared);
        this.l = FastMath.sqrt(lSquared);
        this.n = (config.fissionFraction() * l0Squared + sigmaXSquared) / (l0Squared + (0.5d * sigmaXSquared));
        this.alpha1 = 1.0d / (1.0d + ((0.001d * cloudCenterHeight * windMilesPerHour) / sigma0));
    }

    /**
     * 返回构建模型时使用的原始配置。
     *
     * @return 模型配置
     */
    public FalloutConfig config() {
        return config;
    }

    /**
     * 计算指定点在爆后 1 小时的剂量率。
     *
     * @param x 目标点 x 坐标
     * @param y 目标点 y 坐标
     * @param distanceUnit 输入距离单位
     * @param outputUnit 输出剂量单位
     * @return H+1 剂量率
     */
    public double doseRateHPlus1(double x, double y, DistanceUnit distanceUnit, DoseUnit outputUnit) {
        requireNonNull(distanceUnit, "distanceUnit");
        requireNonNull(outputUnit, "outputUnit");
        Coordinate2D rotated = transform(x, y, distanceUnit);
        double roentgen = cloudFrameHPlus1Roentgen(rotated.x(), rotated.y());
        return UnitConverter.convertDose(roentgen, DoseUnit.ROENTGEN, outputUnit);
    }

    /**
     * 估算沉降到达指定顺风位置所需的时间。
     *
     * @param x 目标点 x 坐标
     * @param distanceUnit 输入距离单位
     * @return 到达时间，单位小时
     */
    public double falloutArrivalTimeHours(double x, DistanceUnit distanceUnit) {
        requireNonNull(distanceUnit, "distanceUnit");
        double xMiles = UnitConverter.convertDistance(x, distanceUnit, DistanceUnit.MILES);
        requireFinite(xMiles, "x");
        double t14 = l0Squared + (0.5d * sigmaXSquared);
        double t15 = l0Squared / lSquared;
        double t10 = xMiles + (2.0d * sigmaX);
        return FastMath.sqrt(0.25d + ((t15 * t10 * t10 * timeConstant * timeConstant * 2.0d * sigmaXSquared) / t14));
    }

    /**
     * 计算 30 天等效残余剂量。
     *
     * @param x 目标点 x 坐标
     * @param y 目标点 y 坐标
     * @param distanceUnit 输入距离单位
     * @param outputUnit 输出剂量单位
     * @return 等效残余剂量
     */
    public double equivalentResidualDose(double x, double y, DistanceUnit distanceUnit, DoseUnit outputUnit) {
        requireNonNull(distanceUnit, "distanceUnit");
        requireNonNull(outputUnit, "outputUnit");
        Coordinate2D rotated = transform(x, y, distanceUnit);
        double bio = bioFactor(rotated.x());
        return doseRateHPlus1(x, y, distanceUnit, outputUnit) * bio;
    }

    /**
     * 返回指定总剂量等值线上的点集。
     */
    public Coordinate2D[] doseContourPoints(double targetDose, DistanceUnit distanceUnit, DoseUnit doseUnit) {
        return doseContourPoints(targetDose, DEFAULT_CONTOUR_POINT_COUNT, distanceUnit, doseUnit);
    }

    /**
     * 返回指定总剂量等值线上的点集。
     */
    public Coordinate2D[] doseContourPoints(double targetDose, int n, DistanceUnit distanceUnit, DoseUnit doseUnit) {
        requireNonNull(distanceUnit, "distanceUnit");
        requireNonNull(doseUnit, "doseUnit");
        double targetRoentgen = UnitConverter.convertDose(targetDose, doseUnit, DoseUnit.ROENTGEN);
        requirePositive(targetRoentgen, "targetDose");
        requireContourPointCount(n);
        return doseContourPointsFromRoentgen(targetRoentgen, n, distanceUnit);
    }

    /**
     * 按输入剂量数组批量返回等值线点集。
     */
    public Coordinate2D[][] doseContourPointSets(double[] targetDoses, DistanceUnit distanceUnit, DoseUnit doseUnit) {
        return doseContourPointSets(targetDoses, DEFAULT_CONTOUR_POINT_COUNT, distanceUnit, doseUnit);
    }

    /**
     * 按输入剂量数组批量返回等值线点集。
     */
    public Coordinate2D[][] doseContourPointSets(double[] targetDoses, int n, DistanceUnit distanceUnit, DoseUnit doseUnit) {
        requireNonNull(distanceUnit, "distanceUnit");
        requireNonNull(doseUnit, "doseUnit");
        requireContourPointCount(n);
        double[] targets = requirePositiveDoseArray(targetDoses, doseUnit);
        Coordinate2D[][] contours = new Coordinate2D[targets.length][];
        for (int index = 0; index < targets.length; index++) {
            contours[index] = doseContourPointsFromRoentgen(targets[index], n, distanceUnit);
        }
        return contours;
    }

    /**
     * 把地图坐标转换为模型内部的顺风/横风坐标。
     *
     * @param x 原始 x 坐标
     * @param y 原始 y 坐标
     * @param distanceUnit 输入距离单位
     * @return 旋转后的二维坐标
     */
    private Coordinate2D transform(double x, double y, DistanceUnit distanceUnit) {
        double xMiles = UnitConverter.convertDistance(x, distanceUnit, DistanceUnit.MILES);
        double yMiles = UnitConverter.convertDistance(y, distanceUnit, DistanceUnit.MILES);
        requireFinite(xMiles, "x");
        requireFinite(yMiles, "y");
        // translatedX / translatedY: 相对爆心平移后的坐标。
        double translatedX = xMiles - groundZeroMilesX;
        double translatedY = yMiles - groundZeroMilesY;
        double rotatedX = (translatedX * rotationCos) - (translatedY * rotationSin);
        double rotatedY = (translatedX * rotationSin) + (translatedY * rotationCos);
        return new Coordinate2D(rotatedX, rotatedY);
    }

    private Coordinate2D cloudToWorld(double rx, double ry) {
        double translatedX = (rx * rotationCos) + (ry * rotationSin);
        double translatedY = (-rx * rotationSin) + (ry * rotationCos);
        return new Coordinate2D(translatedX + groundZeroMilesX, translatedY + groundZeroMilesY);
    }

    private Coordinate2D[] doseContourPointsFromRoentgen(double targetRoentgen, int n, DistanceUnit distanceUnit) {
        if (config.fissionFraction() == 0.0d) {
            throw new IllegalArgumentException("no finite contour exists for the requested dose");
        }
        double scale = FastMath.max(FastMath.max(l, sigmaX), FastMath.max(FastMath.abs(l0), 1.0d));
        double rxMin = -scale;
        double rxMax = scale;
        double[] samples = null;
        double[] centerlineValues = null;

        for (int attempt = 0; attempt < CONTOUR_EXPANSION_LIMIT; attempt++) {
            samples = linearSpace(rxMin, rxMax, CONTOUR_SAMPLE_COUNT);
            centerlineValues = new double[samples.length];
            int maxIndex = 0;
            boolean anyPositive = false;
            for (int index = 0; index < samples.length; index++) {
                centerlineValues[index] = centerlineDoseRoentgen(samples[index]);
                if (centerlineValues[index] > centerlineValues[maxIndex]) {
                    maxIndex = index;
                }
                if (centerlineValues[index] >= targetRoentgen) {
                    anyPositive = true;
                }
            }

            boolean expandLeft = centerlineValues[0] >= targetRoentgen || maxIndex == 0;
            boolean expandRight = centerlineValues[centerlineValues.length - 1] >= targetRoentgen || maxIndex == centerlineValues.length - 1;

            if (anyPositive && !expandLeft && !expandRight) {
                break;
            }
            if (!anyPositive && !expandLeft && !expandRight) {
                throw new IllegalArgumentException("no finite contour exists for the requested dose");
            }
            if (expandLeft) {
                rxMin *= 2.0d;
            }
            if (expandRight) {
                rxMax *= 2.0d;
            }
            if (attempt == CONTOUR_EXPANSION_LIMIT - 1) {
                throw new IllegalArgumentException("unable to bracket a finite contour for the requested dose");
            }
        }

        int firstPositive = -1;
        int lastPositive = -1;
        for (int index = 0; index < centerlineValues.length; index++) {
            if (centerlineValues[index] >= targetRoentgen) {
                if (firstPositive < 0) {
                    firstPositive = index;
                } else if (lastPositive >= 0 && index - lastPositive > 1) {
                    throw new IllegalArgumentException("multiple disjoint contour segments exist for the requested dose");
                }
                lastPositive = index;
            }
        }
        if (firstPositive < 0) {
            throw new IllegalArgumentException("no finite contour exists for the requested dose");
        }

        double contourRxMin = isNearlyEqual(centerlineValues[firstPositive], targetRoentgen)
                ? samples[firstPositive]
                : solveContourRoot(samples[firstPositive - 1], samples[firstPositive], targetRoentgen);
        double contourRxMax = isNearlyEqual(centerlineValues[lastPositive], targetRoentgen)
                ? samples[lastPositive]
                : solveContourRoot(samples[lastPositive], samples[lastPositive + 1], targetRoentgen);

        int upperCount = (n / 2) + 1;
        int lowerCount = n - upperCount + 2;
        Coordinate2D[] points = new Coordinate2D[n];
        int pointIndex = 0;
        for (int index = 0; index < upperCount; index++) {
            double rx = linearValue(contourRxMin, contourRxMax, upperCount, index);
            points[pointIndex++] = contourPoint(rx, 1.0d, targetRoentgen, distanceUnit);
        }
        for (int index = 1; index < lowerCount - 1; index++) {
            double rx = linearValue(contourRxMax, contourRxMin, lowerCount, index);
            points[pointIndex++] = contourPoint(rx, -1.0d, targetRoentgen, distanceUnit);
        }
        return points;
    }

    private Coordinate2D contourPoint(double rx, double sign, double targetRoentgen, DistanceUnit distanceUnit) {
        double peak = centerlineDoseRoentgen(rx);
        CloudFrameComponents components = cloudFrameHPlus1Components(rx);
        double ratio = FastMath.max(0.0d, FastMath.min(1.0d, targetRoentgen / peak));
        double ry = sign * components.alpha2() * components.sY() * FastMath.sqrt(FastMath.max(0.0d, -2.0d * FastMath.log(ratio)));
        Coordinate2D worldMiles = cloudToWorld(rx, ry);
        return new Coordinate2D(
                UnitConverter.convertDistance(worldMiles.x(), DistanceUnit.MILES, distanceUnit),
                UnitConverter.convertDistance(worldMiles.y(), DistanceUnit.MILES, distanceUnit)
        );
    }

    private double centerlineDoseRoentgen(double rx) {
        return cloudFrameHPlus1Roentgen(rx, 0.0d) * bioFactor(rx);
    }

    private double cloudFrameHPlus1Roentgen(double rx, double ry) {
        if (config.fissionFraction() == 0.0d) {
            return 0.0d;
        }
        CloudFrameComponents components = cloudFrameHPlus1Components(rx);
        double fy = FastMath.exp(-0.5d * FastMath.pow(ry / (components.alpha2() * components.sY()), 2.0d))
                / (SQRT_TWO_PI * components.sY());
        return components.fX() * fy;
    }

    private CloudFrameComponents cloudFrameHPlus1Components(double rx) {
        double fX = yieldMegatons * 2_000_000.0d * phi(rx) * g(rx) * config.fissionFraction();
        double sY = FastMath.sqrt(
                sigma0Squared
                        + ((8.0d * FastMath.abs(rx + (2.0d * sigmaX)) * sigma0Squared) / l)
                        + (2.0d * FastMath.pow(sigmaX * timeConstant * sigmaH * shearMilesPerHourPerKilofoot, 2.0d) / lSquared)
                        + (FastMath.pow((rx + (2.0d * sigmaX)) * l0 * timeConstant * sigmaH * shearMilesPerHourPerKilofoot, 2.0d) / FastMath.pow(l, 4.0d))
        );
        double alpha2 = 1.0d / (1.0d
                + ((0.001d * cloudCenterHeight * windMilesPerHour) / sigma0)
                * (1.0d - SpecialFunctions.normalCdf((2.0d * rx) / windMilesPerHour)));
        return new CloudFrameComponents(fX, sY, alpha2);
    }

    private double bioFactor(double rx) {
        double arrival = falloutArrivalTimeHours(rx, DistanceUnit.MILES);
        return FastMath.exp(-(0.287d + (0.52d * FastMath.log(arrival / 31.6d)) + (0.04475d * FastMath.log(FastMath.pow(arrival / 31.6d, 2.0d)))));
    }

    private double solveContourRoot(double lower, double upper, double targetRoentgen) {
        double lowerDifference = centerlineDoseRoentgen(lower) - targetRoentgen;
        if (lowerDifference == 0.0d) {
            return lower;
        }
        double upperDifference = centerlineDoseRoentgen(upper) - targetRoentgen;
        if (upperDifference == 0.0d) {
            return upper;
        }
        double currentLower = lower;
        double currentUpper = upper;
        for (int iteration = 0; iteration < CONTOUR_BISECTION_STEPS; iteration++) {
            double midpoint = (currentLower + currentUpper) * 0.5d;
            double midpointDifference = centerlineDoseRoentgen(midpoint) - targetRoentgen;
            if (midpointDifference == 0.0d) {
                return midpoint;
            }
            if (sameSign(lowerDifference, midpointDifference)) {
                currentLower = midpoint;
                lowerDifference = midpointDifference;
            } else {
                currentUpper = midpoint;
            }
        }
        return (currentLower + currentUpper) * 0.5d;
    }

    private double[] requirePositiveDoseArray(double[] targetDoses, DoseUnit doseUnit) {
        requireNonNull(targetDoses, "targetDoses");
        double[] doses = new double[targetDoses.length];
        for (int index = 0; index < targetDoses.length; index++) {
            doses[index] = UnitConverter.convertDose(targetDoses[index], doseUnit, DoseUnit.ROENTGEN);
            requirePositive(doses[index], "targetDoses[" + index + "]");
        }
        return doses;
    }

    private static double[] linearSpace(double startInclusive, double endInclusive, int count) {
        double[] values = new double[count];
        if (count == 1) {
            values[0] = startInclusive;
            return values;
        }
        double step = (endInclusive - startInclusive) / (count - 1);
        for (int index = 0; index < count; index++) {
            values[index] = startInclusive + (index * step);
        }
        return values;
    }

    private static double linearValue(double startInclusive, double endInclusive, int count, int index) {
        if (count == 1) {
            return startInclusive;
        }
        return startInclusive + ((endInclusive - startInclusive) * index / (count - 1));
    }

    private static boolean sameSign(double left, double right) {
        return (left > 0.0d && right > 0.0d) || (left < 0.0d && right < 0.0d);
    }

    private static boolean isNearlyEqual(double left, double right) {
        return FastMath.abs(left - right) <= 1.0e-12d * FastMath.max(1.0d, FastMath.max(FastMath.abs(left), FastMath.abs(right)));
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0d) {
            throw new IllegalArgumentException(name + " must be greater than 0");
        }
    }

    private static void requireContourPointCount(int n) {
        if (n < MIN_CONTOUR_POINT_COUNT) {
            throw new IllegalArgumentException("n must be greater than or equal to 4");
        }
    }

    private static void requireNonNull(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
    }

    /**
     * 计算顺风方向的沉降分布函数。
     *
     * @param x 顺风距离
     * @return 分布函数值
     */
    private double g(double x) {
        requireFinite(x, "x");
        return FastMath.exp(-FastMath.pow(FastMath.abs(x) / l, n)) / (l * SpecialFunctions.gamma(1.0d + (1.0d / n)));
    }

    /**
     * 计算顺风方向累计分布修正项。
     *
     * @param x 顺风距离
     * @return 累计修正值
     */
    private double phi(double x) {
        requireFinite(x, "x");
        // w: 标准化顺风距离。
        double w = (l0 / l) * (x / (sigmaX * alpha1));
        return SpecialFunctions.normalCdf(w);
    }

    private record CloudFrameComponents(double fX, double sY, double alpha2) {
    }
}
