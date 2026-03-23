package com.glasstone.examples;

import com.glasstone.examples.ui.ContourPlotPanel;
import com.glasstone.fallout.FalloutModel;

import javax.swing.SwingUtilities;

/**
 * Java 版 WSEG-10 伤亡概率等值线示例。
 */
public final class Wseg10CasualtiesExample {
    private static final double[] XS = ExampleSupport.range(-1.0d, 10.0d, 0.05d);
    private static final double[] YS = ExampleSupport.range(-1.0d, 10.0d, 0.05d);
    private static final double[] LEVELS = {0.1d, 0.5d, 0.75d, 0.95d, 1.0d};

    private Wseg10CasualtiesExample() {
    }

    /**
     * 启动示例窗口。
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(Wseg10CasualtiesExample::showExample);
    }

    /**
     * 生成伤亡概率网格并显示等值线。
     */
    private static void showExample() {
        FalloutModel model = ExampleSupport.defaultWseg10Model();
        double[][] values = ExampleSupport.sampleGrid(XS, YS,
                (x, y) -> ExampleSupport.fatalityProbability(model, x, y));

        ContourPlotPanel panel = new ContourPlotPanel(
                "WSEG-10 probability of death for unsheltered individual",
                "st. miles",
                "st. miles",
                "Probability of Death",
                XS,
                YS,
                values,
                LEVELS,
                new java.awt.Color[]{ExampleSupport.BLUE, ExampleSupport.GREEN, ExampleSupport.CYAN, ExampleSupport.YELLOW},
                ExampleSupport.UNDER_COLOR,
                ExampleSupport.OVER_COLOR,
                ExampleSupport.casualtiesDescription(),
                false,
                true,
                XS[0],
                XS[XS.length - 1],
                -0.5d,
                11.0d,
                false,
                -0.7d,
                5.9d
        );
        ExampleSupport.showFrame("WSEG-10 Casualties", panel, 1080, 820);
    }
}
