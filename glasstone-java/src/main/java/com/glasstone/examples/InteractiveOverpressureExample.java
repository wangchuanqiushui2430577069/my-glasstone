package com.glasstone.examples;

import com.glasstone.model.BurstParameters;
import com.glasstone.overpressure.OverpressureCalculator;
import com.glasstone.units.DistanceUnit;
import com.glasstone.units.PressureUnit;
import com.glasstone.units.YieldUnit;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import org.knowm.xchart.XChartPanel;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;

/**
 * Java 版交互式 Brode 峰值静超压示例。
 */
public final class InteractiveOverpressureExample {
    private static final double[] DISTANCES = ExampleSupport.range(0.01d, 25.0d, 0.02d);
    private static final String SERIES_NAME = "peak static overpressure";

    private InteractiveOverpressureExample() {
    }

    /**
     * 启动示例窗口。
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(InteractiveOverpressureExample::showExample);
    }

    /**
     * 构建并展示图表与滑块控件。
     */
    private static void showExample() {
        XYChart chart = new XYChartBuilder()
                .width(900)
                .height(540)
                .title("interactive peak static overpressure calculator")
                .xAxisTitle("distance (kilofeet)")
                .yAxisTitle("peak static overpressure (psi)")
                .build();
        chart.getStyler().setXAxisMin(0.01d);
        chart.getStyler().setXAxisMax(10.0d);
        chart.getStyler().setYAxisMin(0.0d);
        chart.getStyler().setYAxisMax(50.0d);
        chart.getStyler().setMarkerSize(0);
        chart.getStyler().setLegendVisible(false);
        ExampleSupport.applyDarkTheme(chart, ExampleSupport.BLUE);
        chart.addSeries(SERIES_NAME, DISTANCES, overpressureSeries(16.0d, 2.0d));

        XChartPanel<XYChart> chartPanel = new XChartPanel<>(chart);
        ExampleSupport.primeChartLayout(chartPanel);
        JSlider yieldSlider = new JSlider(1, 1000, 16);
        JSlider heightSlider = new JSlider(0, 100, 20);
        JLabel yieldValue = new JLabel("16.0", SwingConstants.RIGHT);
        JLabel heightValue = new JLabel("2.0", SwingConstants.RIGHT);

        // listener: 当量和爆高变化时同步刷新曲线。
        ChangeListener listener = event -> {
            double yield = yieldSlider.getValue();
            double burstHeight = heightSlider.getValue() / 10.0d;
            yieldValue.setText(String.format("%.1f", yield));
            heightValue.setText(String.format("%.1f", burstHeight));
            chart.updateXYSeries(SERIES_NAME, DISTANCES, overpressureSeries(yield, burstHeight), null);
            chartPanel.revalidate();
            chartPanel.repaint();
        };
        yieldSlider.addChangeListener(listener);
        heightSlider.addChangeListener(listener);

        JPanel controls = new JPanel(new GridLayout(2, 3, 10, 6));
        controls.add(new JLabel("Yield (kT):"));
        controls.add(yieldSlider);
        controls.add(yieldValue);
        controls.add(new JLabel("Burst height (kilofeet):"));
        controls.add(heightSlider);
        controls.add(heightValue);

        JPanel content = new JPanel(new BorderLayout());
        content.add(chartPanel, BorderLayout.CENTER);
        content.add(controls, BorderLayout.SOUTH);
        ExampleSupport.showFrame("Interactive Overpressure", content, 980, 700);
    }

    /**
     * 采样一整条超压曲线。
     */
    private static double[] overpressureSeries(double yieldKt, double burstHeightKilofeet) {
        double[] values = new double[DISTANCES.length];
        BurstParameters burst = new BurstParameters(yieldKt, YieldUnit.KILOTONS, burstHeightKilofeet, DistanceUnit.KILOFEET);
        for (int index = 0; index < DISTANCES.length; index++) {
            values[index] = OverpressureCalculator.brodeOverpressure(
                    burst,
                    DISTANCES[index],
                    DistanceUnit.KILOFEET,
                    PressureUnit.PSI
            );
        }
        return values;
    }
}
