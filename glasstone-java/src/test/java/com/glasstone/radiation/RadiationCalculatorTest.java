package com.glasstone.radiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.glasstone.exception.ValueOutsideGraphException;
import com.glasstone.model.BurstParameters;
import com.glasstone.units.DistanceUnit;
import com.glasstone.units.DoseUnit;
import com.glasstone.units.YieldUnit;
import org.junit.jupiter.api.Test;

/**
 * `RadiationCalculator` 的回归测试。
 */
class RadiationCalculatorTest {
    // 验证苏联总穿透辐射剂量与 Python 参考值一致。
    @Test
    void matchesPythonSummaryReferenceValues() {
        assertEquals(5424.167407597681d,
                RadiationCalculator.sovietSummary(1.0d, 500.0d, DistanceUnit.METERS, RadiationScenario.SUMMER, DoseUnit.ROENTGEN),
                1.0e-9);
        assertEquals(3723.346634151684d,
                RadiationCalculator.sovietSummary(1.0d, 500.0d, DistanceUnit.METERS, RadiationScenario.WINTER, DoseUnit.ROENTGEN),
                1.0e-9);
        assertEquals(6565.031508735724d,
                RadiationCalculator.sovietSummary(1.0d, 500.0d, DistanceUnit.METERS, RadiationScenario.MOUNTAIN, DoseUnit.ROENTGEN),
                1.0e-9);
        assertEquals(1122.9978398378576d,
                RadiationCalculator.sovietSummary(20.0d, 1200.0d, DistanceUnit.METERS, RadiationScenario.SUMMER, DoseUnit.ROENTGEN),
                1.0e-9);
        assertEquals(11.229978398378575d,
                RadiationCalculator.sovietSummary(20.0d, 1200.0d, DistanceUnit.METERS, RadiationScenario.SUMMER, DoseUnit.SIEVERT),
                1.0e-12);
    }

    // 验证苏联伽马剂量分量与 Python 参考值一致。
    @Test
    void matchesPythonGammaReferenceValues() {
        assertEquals(2068.5154382650844d,
                RadiationCalculator.sovietGamma(1.0d, 500.0d, DistanceUnit.METERS, RadiationScenario.SUMMER, DoseUnit.ROENTGEN),
                1.0e-9);
        assertEquals(1419.9045523497516d,
                RadiationCalculator.sovietGamma(1.0d, 500.0d, DistanceUnit.METERS, RadiationScenario.WINTER, DoseUnit.ROENTGEN),
                1.0e-9);
        assertEquals(839.6783834357831d,
                RadiationCalculator.sovietGamma(20.0d, 1200.0d, DistanceUnit.METERS, RadiationScenario.SUMMER, DoseUnit.ROENTGEN),
                1.0e-9);
    }

    // 验证苏联中子剂量分量与 Python 参考值一致。
    @Test
    void matchesPythonNeutronReferenceValues() {
        assertEquals(4992.024959064313d,
                RadiationCalculator.sovietNeutron(1.0d, 500.0d, DistanceUnit.METERS, RadiationScenario.SUMMER, DoseUnit.ROENTGEN),
                1.0e-9);
        assertEquals(6823.8582700852d,
                RadiationCalculator.sovietNeutron(1.0d, 500.0d, DistanceUnit.METERS, RadiationScenario.MOUNTAIN, DoseUnit.ROENTGEN),
                1.0e-9);
        assertEquals(404.841264453473d,
                RadiationCalculator.sovietNeutron(20.0d, 1200.0d, DistanceUnit.METERS, RadiationScenario.SUMMER, DoseUnit.ROENTGEN),
                1.0e-9);
    }

    // 验证 Glasstone 裂变武器各剂量分量与总剂量结果。
    @Test
    void matchesGlasstoneFissionReferenceValues() {
        BurstParameters burst = new BurstParameters(20.0d, YieldUnit.KILOTONS, 100.0d, DistanceUnit.METERS);

        assertEquals(399.258355719621d,
                RadiationCalculator.glasstoneFissionSecondaryGamma(burst, 1000.0d, DistanceUnit.METERS, true, DoseUnit.ROENTGEN),
                1.0e-2);
        assertEquals(2179.855343567857d,
                RadiationCalculator.glasstoneFissionSecondaryGamma(burst, 1000.0d, DistanceUnit.METERS, false, DoseUnit.ROENTGEN),
                5.0e-2);
        assertEquals(1043.2869879524897d,
                RadiationCalculator.glasstoneFissionNeutron(burst, 1000.0d, DistanceUnit.METERS, true, DoseUnit.ROENTGEN),
                5.0e-2);
        assertEquals(3938.4026301778786d,
                RadiationCalculator.glasstoneFissionNeutron(burst, 1000.0d, DistanceUnit.METERS, false, DoseUnit.ROENTGEN),
                1.0e-1);
        assertEquals(1904.1137827965488d,
                RadiationCalculator.glasstoneFissionFragmentGamma(burst, 1000.0d, DistanceUnit.METERS, 1.0d, false, DoseUnit.ROENTGEN),
                5.0e-2);
        assertEquals(3346.6591264686595d,
                RadiationCalculator.glasstoneSummary(burst, 1000.0d, DistanceUnit.METERS, 1.0d, true, false, DoseUnit.ROENTGEN),
                1.0e-1);
    }

    @Test
    void supportsOneKilotonBoundaryForEffectiveYieldCurves() {
        BurstParameters burst = new BurstParameters(1.0d, YieldUnit.KILOTONS, 100.0d, DistanceUnit.METERS);

        assertEquals(12225.798397308594d,
                RadiationCalculator.glasstoneSummary(burst, 500.0d, DistanceUnit.METERS, 1.0d, false, false, DoseUnit.ROENTGEN),
                2.0e-1);
        assertEquals(3689.053339460738d,
                RadiationCalculator.glasstoneSummary(burst, 500.0d, DistanceUnit.METERS, 1.0d, true, false, DoseUnit.ROENTGEN),
                1.0e-1);
    }

    @Test
    void preservesPythonEffectiveYieldBoundaryTolerance() {
        BurstParameters slightlyBelowBoundary = new BurstParameters(0.98d, YieldUnit.KILOTONS, 100.0d, DistanceUnit.METERS);

        assertEquals(11998.469983559959d,
                RadiationCalculator.glasstoneSummary(
                        slightlyBelowBoundary,
                        500.0d,
                        DistanceUnit.METERS,
                        1.0d,
                        false,
                        false,
                        DoseUnit.ROENTGEN
                ),
                2.0e-1);
        assertThrows(ValueOutsideGraphException.class,
                () -> RadiationCalculator.glasstoneSummary(
                        new BurstParameters(0.5d, YieldUnit.KILOTONS, 100.0d, DistanceUnit.METERS),
                        500.0d,
                        DistanceUnit.METERS,
                        1.0d,
                        false,
                        false,
                        DoseUnit.ROENTGEN
                ));
    }

    @Test
    void rejectsInvalidFissionFractionInputs() {
        BurstParameters burst = new BurstParameters(20.0d, YieldUnit.KILOTONS, 100.0d, DistanceUnit.METERS);
        double[] invalidFractions = {-0.1d, 1.1d, Double.NaN};

        for (double invalidFraction : invalidFractions) {
            assertThrows(IllegalArgumentException.class,
                    () -> RadiationCalculator.glasstoneSummary(
                            burst,
                            1000.0d,
                            DistanceUnit.METERS,
                            invalidFraction,
                            true,
                            false,
                            DoseUnit.ROENTGEN
                    ));
            assertThrows(IllegalArgumentException.class,
                    () -> RadiationCalculator.glasstoneFissionFragmentGamma(
                            burst,
                            1000.0d,
                            DistanceUnit.METERS,
                            invalidFraction,
                            false,
                            DoseUnit.ROENTGEN
                    ));
            assertThrows(IllegalArgumentException.class,
                    () -> RadiationCalculator.inverseGlasstoneSummary(
                            burst,
                            100.0d,
                            DoseUnit.ROENTGEN,
                            invalidFraction,
                            true,
                            false
                    ));
        }
    }

    // 验证 Glasstone 热核武器分支与单位换算结果。
    @Test
    void matchesGlasstoneThermonuclearReferenceValues() {
        BurstParameters burst = new BurstParameters(100.0d, YieldUnit.KILOTONS, 150.0d, DistanceUnit.METERS);
        BurstParameters fissionFragmentBurst = new BurstParameters(20.0d, YieldUnit.KILOTONS, 150.0d, DistanceUnit.METERS);

        assertEquals(1563.366760680239d,
                RadiationCalculator.glasstoneThermonuclearSecondaryGamma(burst, 1200.0d, DistanceUnit.METERS, DoseUnit.ROENTGEN),
                5.0e-2);
        assertEquals(4249.05808455991d,
                RadiationCalculator.glasstoneThermonuclearNeutron(burst, 1200.0d, DistanceUnit.METERS, DoseUnit.ROENTGEN),
                1.0e-1);
        assertEquals(1549.5952344072825d,
                RadiationCalculator.glasstoneFissionFragmentGamma(fissionFragmentBurst, 1000.0d, DistanceUnit.METERS, 0.7d, true, DoseUnit.ROENTGEN),
                5.0e-2);
        assertEquals(9790.865918656353d,
                RadiationCalculator.glasstoneSummary(burst, 1200.0d, DistanceUnit.METERS, 0.5d, true, true, DoseUnit.ROENTGEN),
                3.0e-1);
        assertEquals(97.90865918656353d,
                RadiationCalculator.glasstoneSummary(burst, 1200.0d, DistanceUnit.METERS, 0.5d, true, true, DoseUnit.SIEVERT),
                3.0e-3);
    }

    @Test
    void invertsGlasstoneGroundBurstSummaryDoseInSievert() {
        BurstParameters groundBurst = new BurstParameters(20.0d, YieldUnit.KILOTONS, 0.0d, DistanceUnit.METERS);
        double targetDoseSv = RadiationCalculator.glasstoneSummary(
                groundBurst,
                1000.0d,
                DistanceUnit.METERS,
                1.0d,
                true,
                true,
                DoseUnit.SIEVERT
        );

        assertEquals(1000.0d,
                RadiationCalculator.inverseGlasstoneSummary(
                        groundBurst,
                        targetDoseSv,
                        DoseUnit.SIEVERT,
                        1.0d,
                        true,
                        true
                ),
                1.0e-6);
        assertEquals(1.0d,
                RadiationCalculator.inverseGlasstoneSummary(
                        groundBurst,
                        targetDoseSv,
                        DoseUnit.SIEVERT,
                        1.0d,
                        true,
                        true,
                        DistanceUnit.KILOMETERS
                ),
                1.0e-9);
    }

    @Test
    void invertsGlasstoneThermonuclearGroundBurstSummaryDose() {
        BurstParameters groundBurst = new BurstParameters(100.0d, YieldUnit.KILOTONS, 0.0d, DistanceUnit.METERS);
        double targetDoseRoentgen = RadiationCalculator.glasstoneSummary(
                groundBurst,
                1200.0d,
                DistanceUnit.METERS,
                0.5d,
                true,
                true,
                DoseUnit.ROENTGEN
        );

        assertEquals(1200.0d,
                RadiationCalculator.inverseGlasstoneSummary(
                        groundBurst,
                        targetDoseRoentgen,
                        DoseUnit.ROENTGEN,
                        0.5d,
                        true,
                        true
                ),
                1.0e-6);
    }

    // 验证辐射模型在输入越界时会抛出图表范围异常。
    @Test
    void rejectsOutOfRangeInputs() {
        assertThrows(ValueOutsideGraphException.class,
                () -> RadiationCalculator.sovietSummary(0.5d, 500.0d, DistanceUnit.METERS, RadiationScenario.SUMMER, DoseUnit.ROENTGEN));
        assertThrows(ValueOutsideGraphException.class,
                () -> RadiationCalculator.sovietGamma(1.0d, 10.0d, DistanceUnit.METERS, RadiationScenario.SUMMER, DoseUnit.ROENTGEN));
        assertThrows(ValueOutsideGraphException.class,
                () -> RadiationCalculator.sovietNeutron(1.0d, -1.0d, DistanceUnit.METERS, RadiationScenario.SUMMER, DoseUnit.ROENTGEN));
        assertThrows(ValueOutsideGraphException.class,
                () -> RadiationCalculator.glasstoneSummary(
                        new BurstParameters(20.0d, YieldUnit.KILOTONS, 100.0d, DistanceUnit.METERS),
                        10.0d,
                        DistanceUnit.METERS,
                        1.0d,
                        true,
                        false,
                        DoseUnit.ROENTGEN
                ));
        assertThrows(ValueOutsideGraphException.class,
                () -> RadiationCalculator.inverseGlasstoneSummary(
                        new BurstParameters(20.0d, YieldUnit.KILOTONS, 0.0d, DistanceUnit.METERS),
                        1.0e8d,
                        DoseUnit.SIEVERT,
                        1.0d,
                        true,
                        true
                ));
    }
}
