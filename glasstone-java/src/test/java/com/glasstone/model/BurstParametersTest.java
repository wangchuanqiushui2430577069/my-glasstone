package com.glasstone.model;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.glasstone.units.DistanceUnit;
import com.glasstone.units.YieldUnit;
import org.junit.jupiter.api.Test;

/**
 * `BurstParameters` 的输入校验测试。
 */
class BurstParametersTest {
    @Test
    void rejectsNonFiniteYield() {
        assertThrows(IllegalArgumentException.class,
                () -> new BurstParameters(Double.NaN, YieldUnit.KILOTONS, 0.0d, DistanceUnit.METERS));
        assertThrows(IllegalArgumentException.class,
                () -> new BurstParameters(Double.POSITIVE_INFINITY, YieldUnit.KILOTONS, 0.0d, DistanceUnit.METERS));
    }

    @Test
    void rejectsNegativeOrNonFiniteBurstHeight() {
        assertThrows(IllegalArgumentException.class,
                () -> new BurstParameters(1.0d, YieldUnit.KILOTONS, -1.0d, DistanceUnit.METERS));
        assertThrows(IllegalArgumentException.class,
                () -> new BurstParameters(1.0d, YieldUnit.KILOTONS, Double.NaN, DistanceUnit.METERS));
        assertThrows(IllegalArgumentException.class,
                () -> new BurstParameters(1.0d, YieldUnit.KILOTONS, Double.POSITIVE_INFINITY, DistanceUnit.METERS));
    }

    @Test
    void rejectsNullUnits() {
        assertThrows(IllegalArgumentException.class,
                () -> new BurstParameters(1.0d, null, 0.0d, DistanceUnit.METERS));
        assertThrows(IllegalArgumentException.class,
                () -> new BurstParameters(1.0d, YieldUnit.KILOTONS, 0.0d, null));
    }
}
