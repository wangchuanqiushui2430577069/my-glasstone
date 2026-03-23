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
 * Java 版交互式 DNA 静压/动压对比示例。
 */
public final class InteractiveDynamicExample {
    private static final double[] DISTANCES = ExampleSupport.range(200.0d, 10_000.0d, 20.0d);
    private static final String STATIC_SERIES = "static overpressure";
    private static final String DYNAMIC_SERIES = "dynamic pressure";

    private InteractiveDynamicExample() {
    }

    /**
     * 启动示例窗口。
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(InteractiveDynamicExample::showExample);
    }

    /**
     * 构建并展示交互式图表。
     */
    private static void showExample() {
        XYChart chart = new XYChartBuilder()
                .width(900)
                .height(540)
                .title("interactive peak static overpressure and dynamic pressure calculator")
                .xAxisTitle("distance (m)")
                .yAxisTitle("peak static overpressure / dynamic pressure (kg/cm^2)")
                .build();
        chart.getStyler().setXAxisMin(200.0d);
        chart.getStyler().setXAxisMax(3000.0d);
        chart.getStyler().setYAxisMin(0.0d);
        chart.getStyler().setYAxisMax(3.5d);
        chart.getStyler().setMarkerSize(0);
        ExampleSupport.applyDarkTheme(chart, ExampleSupport.MAGENTA, ExampleSupport.BLUE);
        chart.addSeries(STATIC_SERIES, DISTANCES, staticSeries(16.0d, 600.0d));
        chart.addSeries(DYNAMIC_SERIES, DISTANCES, dynamicSeries(16.0d, 600.0d));

        XChartPanel<XYChart> chartPanel = new XChartPanel<>(chart);
        ExampleSupport.primeChartLayout(chartPanel);
        JSlider yieldSlider = new JSlider(1, 1000, 16);
        JSlider heightSlider = new JSlider(0, 5000, 600);
        JLabel yieldValue = new JLabel("16.0", SwingConstants.RIGHT);
        JLabel heightValue = new JLabel("600", SwingConstants.RIGHT);

        // listener: 当量或爆高改变时，同时刷新两条曲线。
        ChangeListener listener = event -> {
            double yield = yieldSlider.getValue();
            double burstHeight = heightSlider.getValue();
            yieldValue.setText(String.format("%.1f", yield));
            heightValue.setText(String.format("%.0f", burstHeight));
            chart.updateXYSeries(STATIC_SERIES, DISTANCES, staticSeries(yield, burstHeight), null);
            chart.updateXYSeries(DYNAMIC_SERIES, DISTANCES, dynamicSeries(yield, burstHeight), null);
            chartPanel.revalidate();
            chartPanel.repaint();
        };
        yieldSlider.addChangeListener(listener);
        heightSlider.addChangeListener(listener);

        JPanel controls = new JPanel(new GridLayout(2, 3, 10, 6));
        controls.add(new JLabel("Yield (kT):"));
        controls.add(yieldSlider);
        controls.add(yieldValue);
        controls.add(new JLabel("Burst height (m):"));
        controls.add(heightSlider);
        controls.add(heightValue);

        JPanel content = new JPanel(new BorderLayout());
        content.add(chartPanel, BorderLayout.CENTER);
        content.add(controls, BorderLayout.SOUTH);
        ExampleSupport.showFrame("Interactive Dynamic Pressure", content, 980, 700);
    }

    /**
     * 采样静超压曲线。
     */
    private static double[] staticSeries(double yieldKt, double burstHeightMeters) {
        double[] values = new double[DISTANCES.length];
        BurstParameters burst = new BurstParameters(yieldKt, YieldUnit.KILOTONS, burstHeightMeters, DistanceUnit.METERS);
        for (int index = 0; index < DISTANCES.length; index++) {
            values[index] = OverpressureCalculator.dnaStaticOverpressure(
                    burst,
                    DISTANCES[index],
                    DistanceUnit.METERS,
                    PressureUnit.KILOGRAM_PER_CM2
            );
        }
        return values;
    }

    /**
     * 采样动压曲线。
     */
    private static double[] dynamicSeries(double yieldKt, double burstHeightMeters) {
        double[] values = new double[DISTANCES.length];
        BurstParameters burst = new BurstParameters(yieldKt, YieldUnit.KILOTONS, burstHeightMeters, DistanceUnit.METERS);
        for (int index = 0; index < DISTANCES.length; index++) {
            values[index] = OverpressureCalculator.dnaDynamicPressure(
                    burst,
                    DISTANCES[index],
                    DistanceUnit.METERS,
                    PressureUnit.KILOGRAM_PER_CM2
            );
        }
        return values;
    }
}
