package com.glasstone.examples;

import com.glasstone.fallout.FalloutConfig;
import com.glasstone.fallout.FalloutModel;
import com.glasstone.model.Coordinate2D;
import com.glasstone.model.WindProfile;
import com.glasstone.units.DistanceUnit;
import com.glasstone.units.DoseUnit;
import com.glasstone.units.SpeedUnit;
import com.glasstone.units.WindShearUnit;
import com.glasstone.units.YieldUnit;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.function.DoubleBinaryOperator;
import javax.swing.JComponent;
import javax.swing.JFrame;
import org.knowm.xchart.XChartPanel;
import org.knowm.xchart.XYChart;
import org.hipparchus.distribution.continuous.LogNormalDistribution;
import org.hipparchus.util.FastMath;

/**
 * Java 示例工程的公共辅助工具。
 */
public final class ExampleSupport {
    /**
     * 示例图中复用的主色板。
     */
    public static final Color BLUE = new Color(0x1F77B4);
    public static final Color RED = new Color(0xD62728);
    public static final Color GREEN = new Color(0x2CA02C);
    public static final Color CYAN = new Color(0x17BECF);
    public static final Color YELLOW = new Color(0xFFBF00);
    public static final Color MAGENTA = new Color(0xC2185B);
    public static final Color DODGER_BLUE = new Color(0x1E90FF);
    public static final Color DEEP_PINK = new Color(0xFF1493);
    public static final Color UNDER_COLOR = Color.WHITE;
    public static final Color OVER_COLOR = RED;
    public static final Color DARK_BACKGROUND = new Color(0x101218);
    public static final Color DARK_PANEL = new Color(0x171A22);
    public static final Color DARK_GRID = new Color(0x444A57);
    public static final Color LIGHT_TEXT = new Color(0xF5F7FA);
    public static final Color LIGHT_BORDER = new Color(0x707785);

    /**
     * 用于无掩蔽人员死亡概率估算的对数正态分布。
     */
    private static final LogNormalDistribution UNSHELTERED_FATALITY =
            new LogNormalDistribution(FastMath.log(450.0d), 0.42d);

    /**
     * 私有构造器，禁止实例化工具类。
     */
    private ExampleSupport() {
    }

    /**
     * 生成等步长数值序列。
     */
    public static double[] range(double startInclusive, double endExclusive, double step) {
        if (!(step > 0.0d)) {
            throw new IllegalArgumentException("step must be positive");
        }
        int size = (int) FastMath.ceil((endExclusive - startInclusive) / step);
        double[] values = new double[size];
        for (int index = 0; index < size; index++) {
            values[index] = startInclusive + (step * index);
        }
        return values;
    }

    /**
     * 在二维网格上批量采样一个双变量函数。
     */
    public static double[][] sampleGrid(double[] xs, double[] ys, DoubleBinaryOperator operator) {
        double[][] values = new double[ys.length][xs.length];
        for (int yIndex = 0; yIndex < ys.length; yIndex++) {
            for (int xIndex = 0; xIndex < xs.length; xIndex++) {
                values[yIndex][xIndex] = operator.applyAsDouble(xs[xIndex], ys[yIndex]);
                // if (Double.isFinite(values[yIndex][xIndex])) {
                //     System.out.println("x:" + xIndex +"_y:"+yIndex+"_value:"+values[yIndex][xIndex]);
                // }
            }
        }
        return values;
    }

    /**
     * 在二维网格上批量采样一个双变量函数，并把非有限值替换为 0。
     */
    public static double[][] sampleGrid2(double[] xs, double[] ys, DoubleBinaryOperator operator) {
        double[][] values = new double[ys.length][xs.length];
        for (int yIndex = 0; yIndex < ys.length; yIndex++) {
            for (int xIndex = 0; xIndex < xs.length; xIndex++) {
                double value = operator.applyAsDouble(xs[xIndex], ys[yIndex]);
                values[yIndex][xIndex] = Double.isFinite(value) ? value : 0.0d;
            }
        }
        return values;
    }

    /**
     * 用统一窗口样式展示一个 Swing 组件。
     */
    public static void showFrame(String title, JComponent content, int width, int height) {
        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(content, BorderLayout.CENTER);
        frame.setPreferredSize(new Dimension(width, height));
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    /**
     * 为示例折线图应用与 Python 示例接近的深色主题。
     */
    public static void applyDarkTheme(XYChart chart, Color... seriesColors) {
        chart.getStyler().setChartBackgroundColor(DARK_BACKGROUND);
        chart.getStyler().setPlotBackgroundColor(DARK_BACKGROUND);
        chart.getStyler().setPlotGridLinesVisible(true);
        chart.getStyler().setPlotGridLinesColor(DARK_GRID);
        chart.getStyler().setPlotBorderVisible(false);
        chart.getStyler().setChartFontColor(LIGHT_TEXT);
        chart.getStyler().setXAxisTitleColor(LIGHT_TEXT);
        chart.getStyler().setYAxisTitleColor(LIGHT_TEXT);
        chart.getStyler().setXAxisTickLabelsColor(LIGHT_TEXT);
        chart.getStyler().setYAxisTickLabelsColor(LIGHT_TEXT);
        chart.getStyler().setAxisTickMarksColor(LIGHT_TEXT);
        chart.getStyler().setLegendBackgroundColor(new Color(DARK_PANEL.getRed(), DARK_PANEL.getGreen(), DARK_PANEL.getBlue(), 220));
        chart.getStyler().setLegendBorderColor(LIGHT_BORDER);
        chart.getStyler().setCursorEnabled(true);
        chart.getStyler().setCursorColor(RED);
        chart.getStyler().setCursorLineWidth(2.0f);
        if (seriesColors.length > 0) {
            chart.getStyler().setSeriesColors(seriesColors);
        }
    }

    /**
     * 预先离屏绘制一次图表面板，避免 XChart 的鼠标光标在首帧前访问未初始化的 plot bounds。
     */
    public static void primeChartLayout(XChartPanel<XYChart> chartPanel) {
        Dimension preferredSize = chartPanel.getPreferredSize();
        chartPanel.setSize(preferredSize);
        BufferedImage image = new BufferedImage(preferredSize.width, preferredSize.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            chartPanel.paint(graphics);
        } finally {
            graphics.dispose();
        }
    }

    /**
     * 构造与 Python 示例一致的默认 WSEG-10 模型。
     */
    public static FalloutModel defaultWseg10Model() {
        WindProfile windProfile = new WindProfile(
                1.151515d * 2.0d,
                SpeedUnit.MILES_PER_HOUR,
                225.0d,
                0.23d,
                WindShearUnit.MILES_PER_HOUR_PER_KILOFOOT
        );
        FalloutConfig config = new FalloutConfig(
                new Coordinate2D(1.0d, 1.0d),
                DistanceUnit.MILES,
                0.01d,
                YieldUnit.MEGATONS,
                1.0d,
                windProfile,
                0.0d
        );
        return new FalloutModel(config);
    }

    /**
     * 返回默认 WSEG-10 示例的文字说明。
     */
    public static String defaultWseg10Description() {
        return "Wind: SW, 2.30303 mi/hr\n"
                + "Shear: 0.23 mi/hr-kilofeet\n"
                + "Yield: 10kT\n"
                + "GZ: 1,1\n"
                + "FF: 1.0\n"
                + "HOB: 0";
    }

    /**
     * 返回伤亡概率图使用的补充说明。
     */
    public static String casualtiesDescription() {
        return defaultWseg10Description()
                + "\nLD10 = 263R\n"
                + "LD50 = 450R\n"
                + "LD95 = 900R";
    }

    /**
     * 根据等效残余剂量计算无遮蔽人员死亡概率。
     */
    public static double fatalityProbability(FalloutModel model, double xMiles, double yMiles) {
        double dose = model.equivalentResidualDose(xMiles, yMiles, DistanceUnit.MILES, DoseUnit.ROENTGEN);
        if (dose > 2000.0d) {
            return 1.01d;
        }
        return UNSHELTERED_FATALITY.cumulativeProbability(dose);
    }

    /**
     * 对数值做限幅处理。
     */
    public static double clamp(double value, double minimum, double maximum) {
        return FastMath.max(minimum, FastMath.min(maximum, value));
    }
}
