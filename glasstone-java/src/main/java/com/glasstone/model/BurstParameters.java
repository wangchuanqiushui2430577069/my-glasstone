package com.glasstone.model;

import com.glasstone.units.DistanceUnit;
import com.glasstone.units.YieldUnit;

/**
 * 描述一次爆炸计算所需的基础输入：当量、当量单位、爆高及其距离单位。
 *
 * @param yield 爆炸当量数值
 * @param yieldUnit 当量单位
 * @param burstHeight 爆高数值
 * @param distanceUnit 爆高所使用的距离单位
 */
public record BurstParameters(
        double yield,
        YieldUnit yieldUnit,
        double burstHeight,
        DistanceUnit distanceUnit
) {
    /**
     * 校验爆炸输入的物理合理性。
     */
    public BurstParameters {
        requirePositiveFinite(yield, "yield");
        requireNonNegativeFinite(burstHeight, "burstHeight");
        requireNonNull(yieldUnit, "yieldUnit");
        requireNonNull(distanceUnit, "distanceUnit");
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0d) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
    }

    private static void requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0d) {
            throw new IllegalArgumentException(name + " must be non-negative and finite");
        }
    }

    private static void requireNonNull(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
    }
}
