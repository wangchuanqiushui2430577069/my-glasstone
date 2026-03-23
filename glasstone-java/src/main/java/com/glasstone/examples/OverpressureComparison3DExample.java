package com.glasstone.examples;

import com.glasstone.examples.ui.SurfacePlotPanel;
import com.glasstone.examples.ui.SurfaceSeries;
import com.glasstone.model.BurstParameters;
import com.glasstone.overpressure.OverpressureCalculator;
import com.glasstone.units.DistanceUnit;
import com.glasstone.units.PressureUnit;
import com.glasstone.units.YieldUnit;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * Java 版 3D 超压模型对比示例。
 */
public final class OverpressureComparison3DExample {
    private static final double[] BURST_HEIGHTS = ExampleSupport.range(70.0d, 200.0d, 1.0d);
    private static final double[] GROUND_RANGES = ExampleSupport.range(70.0d, 200.0d, 1.0d);

    private OverpressureComparison3DExample() {
    }

    /**
     * 启动示例窗口。
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(OverpressureComparison3DExample::showExample);
    }

    /**
     * 生成美国/苏联模型对比曲面并显示。
     */
    private static void showExample() {
        double[][] american = ExampleSupport.sampleGrid(BURST_HEIGHTS, GROUND_RANGES, (burstHeight, groundRange) ->
                OverpressureCalculator.brodeOverpressure(
                        new BurstParameters(1.0d, YieldUnit.KILOTONS, burstHeight, DistanceUnit.METERS),
                        groundRange,
                        DistanceUnit.METERS,
                        PressureUnit.KILOGRAM_PER_CM2
                ));
        double[][] soviet = ExampleSupport.sampleGrid(BURST_HEIGHTS, GROUND_RANGES, (burstHeight, groundRange) ->
                OverpressureCalculator.sovietOverpressure(
                        new BurstParameters(1.0d, YieldUnit.KILOTONS, burstHeight, DistanceUnit.METERS),
                        groundRange,
                        DistanceUnit.METERS,
                        true,
                        PressureUnit.KILOGRAM_PER_CM2
                ));

        SurfacePlotPanel panel = new SurfacePlotPanel(
                "peak static overpressure for 1kT burst",
                "burst height (m)",
                "ground range (m)",
                "peak static overpressure (kg/cm^2)",
                BURST_HEIGHTS,
                GROUND_RANGES,
                List.of(
                        new SurfaceSeries("U.S. (ideal surface)", ExampleSupport.BLUE, american, 7, 7),
                        new SurfaceSeries("Soviet (thermal precursor)", ExampleSupport.RED, soviet, 7, 7)
                ),
                0.0d,
                23.0d
        );
        panel.setThemeColors(
                ExampleSupport.DARK_BACKGROUND,
                ExampleSupport.LIGHT_TEXT,
                new java.awt.Color(ExampleSupport.DARK_PANEL.getRed(), ExampleSupport.DARK_PANEL.getGreen(), ExampleSupport.DARK_PANEL.getBlue(), 220),
                ExampleSupport.LIGHT_BORDER
        );
        panel.setViewAngles(300.0d, 30.0d);
        ExampleSupport.showFrame("3D Overpressure Comparison", panel, 1100, 840);
    }
}
