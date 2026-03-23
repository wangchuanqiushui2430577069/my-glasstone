package com.glasstone.api;

import com.glasstone.model.BurstParameters;
import com.glasstone.overpressure.OverpressureCalculator;
import com.glasstone.units.DistanceUnit;
import com.glasstone.units.PressureUnit;
import com.glasstone.units.YieldUnit;

/**
 * 面向调用方的 Brode 批量超压反算 API。
 * <p>
 * 该门面与 Python 版 `brode_overpressure_distances(...)` 对齐：
 * 默认使用 `kT`、`m`、`kg/cm^2`，并对每个目标超压返回离爆点最近的那条距离解。
 */
public final class OverPressureAPI {
    private static final int DEFAULT_SAMPLE_COUNT = 100;

    private OverPressureAPI() {
    }

    /**
     * 使用 Python 对齐的默认单位批量反算目标静超压对应的地面距离。
     */
    public static double[] brodeOverpressureDistances(double[] targetOps, double yield, double burstHeight) {
        return brodeOverpressureDistances(targetOps, yield, burstHeight, DEFAULT_SAMPLE_COUNT);
    }

    /**
     * 使用 Python 对齐的默认单位批量反算目标静超压对应的地面距离，并允许指定采样密度。
     */
    public static double[] brodeOverpressureDistances(double[] targetOps, double yield, double burstHeight, int n) {
        return brodeOverpressureDistances(
                targetOps,
                yield,
                burstHeight,
                n,
                YieldUnit.KILOTONS,
                DistanceUnit.METERS,
                PressureUnit.KILOGRAM_PER_CM2
        );
    }

    /**
     * 使用显式单位批量反算目标静超压对应的地面距离。
     */
    public static double[] brodeOverpressureDistances(
            double[] targetOps,
            double yield,
            double burstHeight,
            int n,
            YieldUnit yieldUnit,
            DistanceUnit distanceUnit,
            PressureUnit pressureUnit
    ) {
        requireNonNull(targetOps, "targetOps");
        requireNonNull(yieldUnit, "yieldUnit");
        requireNonNull(distanceUnit, "distanceUnit");
        requireNonNull(pressureUnit, "pressureUnit");

        BurstParameters burst = new BurstParameters(yield, yieldUnit, burstHeight, distanceUnit);
        double[] distances = new double[targetOps.length];
        for (int index = 0; index < targetOps.length; index++) {
            double target = targetOps[index];
            if (!Double.isFinite(target) || target <= 0.0d) {
                throw new IllegalArgumentException("targetOps[" + index + "] must be positive and finite");
            }
            distances[index] = OverpressureCalculator.inverseBrodeOverpressure(
                    burst,
                    target,
                    pressureUnit,
                    distanceUnit,
                    n
            );
        }
        return distances;
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}
