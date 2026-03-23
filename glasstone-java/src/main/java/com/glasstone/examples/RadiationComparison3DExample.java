package com.glasstone.examples;

import com.glasstone.exception.ValueOutsideGraphException;
import com.glasstone.examples.ui.SurfacePlotPanel;
import com.glasstone.examples.ui.SurfaceSeries;
import com.glasstone.model.BurstParameters;
import com.glasstone.radiation.RadiationCalculator;
import com.glasstone.radiation.RadiationScenario;
import com.glasstone.units.DistanceUnit;
import com.glasstone.units.DoseUnit;
import com.glasstone.units.YieldUnit;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * Java 版 3D 电离辐射对比示例。
 */
public final class RadiationComparison3DExample {
    private static final double[] YIELDS = ExampleSupport.range(1.0d, 10.0d, 0.1d);
    private static final double[] SLANT_RANGES = ExampleSupport.range(500.0d, 1000.0d, 5.0d);
    private static final double Z_MIN = 20.0d;
    private static final double Z_MAX = 125_000.0d;

    private RadiationComparison3DExample() {
    }

    /**
     * 启动示例窗口。
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(RadiationComparison3DExample::showExample);
    }

    /**
     * 生成美国/苏联剂量曲面并显示。
     */
    private static void showExample() {
        double[][] sovietSummer = ExampleSupport.sampleGrid(YIELDS, SLANT_RANGES,
                (yield, slantRange) -> clippedSoviet(yield, slantRange, RadiationScenario.SUMMER));
        double[][] sovietMountain = ExampleSupport.sampleGrid(YIELDS, SLANT_RANGES,
                (yield, slantRange) -> clippedSoviet(yield, slantRange, RadiationScenario.MOUNTAIN));
        double[][] sovietWinter = ExampleSupport.sampleGrid(YIELDS, SLANT_RANGES,
                (yield, slantRange) -> clippedSoviet(yield, slantRange, RadiationScenario.WINTER));
        double[][] americanDefense = ExampleSupport.sampleGrid(YIELDS, SLANT_RANGES,
                (yield, slantRange) -> clippedGlasstone(yield, slantRange, false));
        double[][] americanOffense = ExampleSupport.sampleGrid(YIELDS, SLANT_RANGES,
                (yield, slantRange) -> clippedGlasstone(yield, slantRange, true));

        SurfacePlotPanel panel = new SurfacePlotPanel(
                "penetrating radiation from low-yield fission airbursts",
                "yield (kT)",
                "slant range (m)",
                "summary dose (rads)",
                YIELDS,
                SLANT_RANGES,
                List.of(
                        new SurfaceSeries("U.S. (defense)", ExampleSupport.BLUE, americanDefense, 4, 4),
                        new SurfaceSeries("Soviet (mountain)", ExampleSupport.RED, sovietMountain, 4, 4),
                        new SurfaceSeries("Soviet (summer)", ExampleSupport.DEEP_PINK, sovietSummer, 4, 4),
                        new SurfaceSeries("U.S. (offense)", ExampleSupport.DODGER_BLUE, americanOffense, 4, 4),
                        new SurfaceSeries("Soviet (winter)", ExampleSupport.MAGENTA, sovietWinter, 4, 4)
                ),
                YIELDS[0],
                YIELDS[YIELDS.length - 1],
                SLANT_RANGES[SLANT_RANGES.length - 1],
                SLANT_RANGES[0],
                Z_MIN,
                Z_MAX
        );
        panel.setViewAngles(300.0d, 30.0d);
        ExampleSupport.showFrame("3D Radiation Comparison", panel, 1180, 840);
    }

    /**
     * 安全计算苏联模型剂量，并把结果裁剪到显示范围内。
     */
    private static double clippedSoviet(double yieldKt, double slantRangeMeters, RadiationScenario scenario) {
        try {
            double dose = RadiationCalculator.sovietSummary(
                    yieldKt,
                    slantRangeMeters,
                    DistanceUnit.METERS,
                    scenario,
                    DoseUnit.ROENTGEN
            );
            return ExampleSupport.clamp(dose, Z_MIN, Z_MAX);
        } catch (ValueOutsideGraphException exception) {
            return slantRangeMeters > 1400.0d ? Z_MIN : Z_MAX;
        }
    }

    /**
     * 计算 Glasstone 模型剂量，并把结果裁剪到显示范围内。
     */
    private static double clippedGlasstone(double yieldKt, double slantRangeMeters, boolean offense) {
        BurstParameters burst = new BurstParameters(yieldKt, YieldUnit.KILOTONS, 100.0d, DistanceUnit.METERS);
        double dose = RadiationCalculator.glasstoneSummary(
                burst,
                slantRangeMeters,
                DistanceUnit.METERS,
                1.0d,
                offense,
                false,
                DoseUnit.ROENTGEN
        );
        return ExampleSupport.clamp(dose, Z_MIN, Z_MAX);
    }
}
