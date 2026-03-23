package com.glasstone.units;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * `UnitConverter` 的回归测试。
 */
class UnitConverterTest {
    // 验证距离换算结果与 Python 版本保持一致。
    @Test
    void convertsDistanceUnitsLikePythonVersion() {
        assertEquals(0.621372736649807d,
                UnitConverter.convertDistance(1.0d, DistanceUnit.KILOMETERS, DistanceUnit.MILES),
                1.0e-12);
        assertEquals(1.60934d,
                UnitConverter.convertDistance(1.0d, DistanceUnit.MILES, DistanceUnit.KILOMETERS),
                1.0e-12);
    }

    // 验证压力换算结果与 Python 版本保持一致。
    @Test
    void convertsPressureUnitsLikePythonVersion() {
        assertEquals(0.006894757280343447d,
                UnitConverter.convertPressure(1.0d, PressureUnit.PSI, PressureUnit.MEGAPASCAL),
                1.0e-15);
        assertEquals(10.197168245565999d,
                UnitConverter.convertPressure(1.0d, PressureUnit.MEGAPASCAL, PressureUnit.KILOGRAM_PER_CM2),
                1.0e-12);
    }

    // 验证速度和风切变换算结果与 Python 版本保持一致。
    @Test
    void convertsSpeedAndWindShearLikePythonVersion() {
        assertEquals(0.4470392589877243d,
                UnitConverter.convertSpeed(1.0d, SpeedUnit.MILES_PER_HOUR, SpeedUnit.METERS_PER_SECOND),
                1.0e-15);
        assertEquals(0.13625756613945836d,
                UnitConverter.convertWindShear(
                        1.0d,
                        WindShearUnit.METERS_PER_SECOND_PER_KILOMETER,
                        WindShearUnit.MILES_PER_HOUR_PER_KILOFOOT),
                1.0e-15);
    }

    // 验证剂量单位换算结果与 Python 版本保持一致。
    @Test
    void convertsDoseLikePythonVersion() {
        assertEquals(1.0d,
                UnitConverter.convertDose(100.0d, DoseUnit.ROENTGEN, DoseUnit.SIEVERT),
                1.0e-15);
    }
}
