package com.glasstone.thermal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.glasstone.exception.ValueOutsideGraphException;
import com.glasstone.model.BurstParameters;
import com.glasstone.units.DistanceUnit;
import com.glasstone.units.YieldUnit;
import org.junit.jupiter.api.Test;

/**
 * `ThermalCalculator` 的回归测试。
 */
class ThermalCalculatorTest {
    // 验证苏联空爆热冲量正算结果与 Python 参考值一致。
    @Test
    void matchesPythonAirThermalReferenceValues() {
        BurstParameters lowAirBurst = new BurstParameters(1.0d, YieldUnit.KILOTONS, 100.0d, DistanceUnit.METERS);
        BurstParameters mediumGroundLikeBurst = new BurstParameters(10.0d, YieldUnit.KILOTONS, 0.0d, DistanceUnit.METERS);
        BurstParameters highAirBurst = new BurstParameters(250.0d, YieldUnit.KILOTONS, 500.0d, DistanceUnit.METERS);

        assertEquals(3.019412197029541d,
                ThermalCalculator.sovietAirThermal(lowAirBurst, 1000.0d, DistanceUnit.METERS, 9),
                1.0e-12);
        assertEquals(17.294358913423512d,
                ThermalCalculator.sovietAirThermal(mediumGroundLikeBurst, 1200.0d, DistanceUnit.METERS, 5),
                1.0e-12);
        assertEquals(18.578557907982237d,
                ThermalCalculator.sovietAirThermal(highAirBurst, 3000.0d, DistanceUnit.METERS, 3),
                1.0e-12);
    }

    // 验证苏联地爆热冲量正算结果与 Python 参考值一致。
    @Test
    void matchesPythonGroundThermalReferenceValues() {
        BurstParameters clearBurst = new BurstParameters(1.0d, YieldUnit.KILOTONS, 0.0d, DistanceUnit.METERS);
        BurstParameters mediumBurst = new BurstParameters(10.0d, YieldUnit.KILOTONS, 0.0d, DistanceUnit.METERS);
        BurstParameters hazyBurst = new BurstParameters(250.0d, YieldUnit.KILOTONS, 0.0d, DistanceUnit.METERS);

        assertEquals(0.993235892107108d,
                ThermalCalculator.sovietGroundThermal(clearBurst, 1000.0d, DistanceUnit.METERS, 9),
                1.0e-12);
        assertEquals(5.688980795895839d,
                ThermalCalculator.sovietGroundThermal(mediumBurst, 1200.0d, DistanceUnit.METERS, 5),
                1.0e-12);
        assertEquals(1.637867805973529d,
                ThermalCalculator.sovietGroundThermal(hazyBurst, 3000.0d, DistanceUnit.METERS, 3),
                1.0e-12);
    }

    // 验证苏联热冲量反算距离结果与 Python 参考值一致。
    @Test
    void matchesPythonInverseThermalReferenceValues() {
        BurstParameters lowAirBurst = new BurstParameters(1.0d, YieldUnit.KILOTONS, 100.0d, DistanceUnit.METERS);
        BurstParameters mediumBurst = new BurstParameters(10.0d, YieldUnit.KILOTONS, 0.0d, DistanceUnit.METERS);
        BurstParameters lowGroundBurst = new BurstParameters(1.0d, YieldUnit.KILOTONS, 0.0d, DistanceUnit.METERS);

        assertEquals(767.5381375536474d,
                ThermalCalculator.inverseSovietAirThermal(lowAirBurst, 5.2d, 9, DistanceUnit.METERS),
                1.0e-9);
        assertEquals(1711.6247658300497d,
                ThermalCalculator.inverseSovietAirThermal(mediumBurst, 7.5d, 5, DistanceUnit.METERS),
                1.0e-9);
        assertEquals(594.7656656575548d,
                ThermalCalculator.inverseSovietGroundThermal(lowGroundBurst, 3.0d, 9, DistanceUnit.METERS),
                1.0e-9);
        assertEquals(1177.6115837586756d,
                ThermalCalculator.inverseSovietGroundThermal(mediumBurst, 6.0d, 5, DistanceUnit.METERS),
                1.0e-9);
    }

    // 验证热模型在输入越界时会抛出图表范围异常。
    @Test
    void rejectsOutOfRangeInputs() {
        BurstParameters burst = new BurstParameters(1.0d, YieldUnit.KILOTONS, 0.0d, DistanceUnit.METERS);

        assertThrows(ValueOutsideGraphException.class,
                () -> ThermalCalculator.sovietAirThermal(burst, 10.0d, DistanceUnit.METERS, 9));
        assertThrows(ValueOutsideGraphException.class,
                () -> ThermalCalculator.sovietAirThermal(burst, 1000.0d, DistanceUnit.METERS, 0));
    }
}
