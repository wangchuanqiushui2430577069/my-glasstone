package com.glasstone.api;

import com.glasstone.fallout.FalloutConfig;
import com.glasstone.fallout.FalloutModel;
import com.glasstone.model.Coordinate2D;
import com.glasstone.model.WindProfile;
import com.glasstone.units.DistanceUnit;
import com.glasstone.units.DoseUnit;
import com.glasstone.units.SpeedUnit;
import com.glasstone.units.WindShearUnit;
import com.glasstone.units.YieldUnit;

/**
 * 面向调用方的放射性沉降批量等剂量轮廓 API。
 * <p>
 * 该门面与 Python 版 `WSEG10.dose_contour_point_sets(...)` 对齐：
 * 默认使用 `km`、`Sv`、`km/h`、`m/s-km`、`kT`，返回值按
 * `[目标剂量索引][轮廓点索引]` 组织。
 */
public final class FalloutAPI {
    private static final int DEFAULT_CONTOUR_POINT_COUNT = 100;
    private static final double DEFAULT_TIME_OF_BURST_HOURS = 0.0d;
    private static final double DEFAULT_ZERO_X = 0.0d;
    private static final double DEFAULT_ZERO_Y = 0.0d;    
    private static final double DEFAULT_WIND_SHEAR = 0.23d;
    private static final double DEFAULT_WIND_SPEED = 2.30303d;
    private static final double DEFAULT_FF = 1.0d;

    private FalloutAPI() {
    }

    /**
     * 快速反算对应剂量所对应的位置点集
     * @param yield 当量
     * @param targetDoses 目标剂量值数组
     * @param windDirectionDegrees 风向，正北为0
     * @return 
     */
    public static Coordinate2D[][] quickDoseContourPointeSets(
        double yield,
        double[] targetDoses,
        double windDirectionDegrees
    ) {
        return doseContourPointSets(
                DEFAULT_ZERO_X,
                DEFAULT_ZERO_Y,
                yield,
                DEFAULT_FF,
                DEFAULT_WIND_SPEED,
                windDirectionDegrees,
                DEFAULT_WIND_SHEAR,
                targetDoses,
                DEFAULT_CONTOUR_POINT_COUNT
        );
    }

    /**
     * 使用 Python 对齐的默认单位批量计算总剂量等值线点集。
     */
    public static Coordinate2D[][] doseContourPointSets(
            double groundZeroX,
            double groundZeroY,
            double yield,
            double fissionFraction,
            double windSpeed,
            double windDirectionDegrees,
            double windShear,
            double[] targetDoses
    ) {
        return doseContourPointSets(
                groundZeroX,
                groundZeroY,
                yield,
                fissionFraction,
                windSpeed,
                windDirectionDegrees,
                windShear,
                targetDoses,
                DEFAULT_CONTOUR_POINT_COUNT
        );
    }

    /**
     * 使用 Python 对齐的默认单位批量计算总剂量等值线点集，并允许指定点数。
     */
    public static Coordinate2D[][] doseContourPointSets(
            double groundZeroX,
            double groundZeroY,
            double yield,
            double fissionFraction,
            double windSpeed,
            double windDirectionDegrees,
            double windShear,
            double[] targetDoses,
            int n
    ) {
        return doseContourPointSets(
                groundZeroX,
                groundZeroY,
                yield,
                fissionFraction,
                windSpeed,
                windDirectionDegrees,
                windShear,
                DEFAULT_TIME_OF_BURST_HOURS,
                targetDoses,
                n,
                DistanceUnit.KILOMETERS,
                DoseUnit.SIEVERT,
                SpeedUnit.KILOMETERS_PER_HOUR,
                WindShearUnit.METERS_PER_SECOND_PER_KILOMETER,
                YieldUnit.KILOTONS
        );
    }

    /**
     * 使用显式单位批量计算总剂量等值线点集。
     */
    public static Coordinate2D[][] doseContourPointSets(
            double groundZeroX,
            double groundZeroY,
            double yield,
            double fissionFraction,
            double windSpeed,
            double windDirectionDegrees,
            double windShear,
            double[] targetDoses,
            int n,
            DistanceUnit distanceUnit,
            DoseUnit doseUnit,
            SpeedUnit speedUnit,
            WindShearUnit windShearUnit,
            YieldUnit yieldUnit
    ) {
        return doseContourPointSets(
                groundZeroX,
                groundZeroY,
                yield,
                fissionFraction,
                windSpeed,
                windDirectionDegrees,
                windShear,
                DEFAULT_TIME_OF_BURST_HOURS,
                targetDoses,
                n,
                distanceUnit,
                doseUnit,
                speedUnit,
                windShearUnit,
                yieldUnit
        );
    }

    /**
     * 使用显式单位和爆炸时刻批量计算总剂量等值线点集。
     */
    public static Coordinate2D[][] doseContourPointSets(
            double groundZeroX,
            double groundZeroY,
            double yield,
            double fissionFraction,
            double windSpeed,
            double windDirectionDegrees,
            double windShear,
            double timeOfBurstHours,
            double[] targetDoses,
            int n,
            DistanceUnit distanceUnit,
            DoseUnit doseUnit,
            SpeedUnit speedUnit,
            WindShearUnit windShearUnit,
            YieldUnit yieldUnit
    ) {
        FalloutConfig config = new FalloutConfig(
                new Coordinate2D(groundZeroX, groundZeroY),
                requireNonNull(distanceUnit, "distanceUnit"),
                yield,
                requireNonNull(yieldUnit, "yieldUnit"),
                fissionFraction,
                new WindProfile(
                        windSpeed,
                        requireNonNull(speedUnit, "speedUnit"),
                        windDirectionDegrees,
                        windShear,
                        requireNonNull(windShearUnit, "windShearUnit")
                ),
                timeOfBurstHours
        );
        return new FalloutModel(config).doseContourPointSets(
                requireNonNull(targetDoses, "targetDoses"),
                n,
                distanceUnit,
                requireNonNull(doseUnit, "doseUnit")
        );
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}
