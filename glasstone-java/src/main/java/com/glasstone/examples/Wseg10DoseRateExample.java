package com.glasstone.examples;

import com.glasstone.examples.ui.ContourPlotPanel;
import com.glasstone.fallout.FalloutModel;
import com.glasstone.units.DistanceUnit;
import com.glasstone.units.DoseUnit;
import javax.swing.SwingUtilities;

/**
 * Java 版 WSEG-10 H+1 剂量率等值线示例。
 */
public final class Wseg10DoseRateExample {
    private static final double[] XS = ExampleSupport.range(-1.0d, 10.0d, 0.1d);
    private static final double[] YS = ExampleSupport.range(-1.0d, 10.0d, 0.1d);
    private static final double[] LEVELS = {100.0d, 300.0d, 500.0d, 1000.0d, 3000.0d};

    private Wseg10DoseRateExample() {
    }

    /**
     * 启动示例窗口。
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(Wseg10DoseRateExample::showExample);
    }

    /**
     * 生成剂量率网格并显示等值线。
     */
    private static void showExample() {
        FalloutModel model = ExampleSupport.defaultWseg10Model();
        double[][] values = ExampleSupport.sampleGrid(XS, YS,
                (x, y) -> model.doseRateHPlus1(x, y, DistanceUnit.MILES, DoseUnit.ROENTGEN));

        ContourPlotPanel panel = new ContourPlotPanel(
                "WSEG-10 H+1 dose rate contours for 10kT burst",
                "st. miles",
                "st. miles",
                "H+1 dose rate (R/hr)",
                XS,
                YS,
                values,
                LEVELS,
                new java.awt.Color[]{ExampleSupport.BLUE, ExampleSupport.GREEN, ExampleSupport.CYAN, ExampleSupport.YELLOW},
                ExampleSupport.UNDER_COLOR,
                ExampleSupport.OVER_COLOR,
                ExampleSupport.defaultWseg10Description(),
                false,
                true,
                XS[0],
                XS[XS.length - 1],
                -0.5d,
                11.0d,
                false,
                -0.5d,
                7.5d
        );
        ExampleSupport.showFrame("WSEG-10 Dose Rate", panel, 1080, 820);
    }
}
