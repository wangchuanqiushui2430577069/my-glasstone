package com.glasstone.examples;

import com.glasstone.examples.ui.ContourPlotPanel;
import com.glasstone.fallout.FalloutModel;
import com.glasstone.units.DistanceUnit;
import com.glasstone.units.DoseUnit;
import javax.swing.SwingUtilities;

/**
 * Java 版 WSEG-10 30 天总剂量等值线示例。
 */
public final class Wseg10DoseExample {
    private static final double[] XS = ExampleSupport.range(-1.0d, 10.0d, 0.1d);
    private static final double[] YS = ExampleSupport.range(-1.0d, 10.0d, 0.1d);
    private static final double[] LEVELS = {500.0d, 1000.0d, 3000.0d, 10_000.0d, 20_000.0d};

    private Wseg10DoseExample() {
    }

    /**
     * 启动示例窗口。
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(Wseg10DoseExample::showExample);
    }

    /**
     * 生成等效残余剂量网格并显示等值线。
     */
    private static void showExample() {
        FalloutModel model = ExampleSupport.defaultWseg10Model();
        double[][] values = ExampleSupport.sampleGrid(XS, YS,
                (x, y) -> model.equivalentResidualDose(x, y, DistanceUnit.MILES, DoseUnit.ROENTGEN));

        ContourPlotPanel panel = new ContourPlotPanel(
                "WSEG-10 30-day total dose contours for 10kT burst",
                "st. miles",
                "st. miles",
                "Equivalent Residual Dose (Roentgens)",
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
        ExampleSupport.showFrame("WSEG-10 Total Dose", panel, 1080, 820);
    }
}
