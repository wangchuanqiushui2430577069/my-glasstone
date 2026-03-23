package com.glasstone.examples;

import com.glasstone.exception.ValueOutsideGraphException;
import com.glasstone.model.BurstParameters;
import com.glasstone.thermal.ThermalCalculator;
import com.glasstone.units.DistanceUnit;
import com.glasstone.units.YieldUnit;
import java.awt.GridLayout;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.knowm.xchart.XChartPanel;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;

/**
 * Java 版总热冲量对比示例。
 */
public final class ThermalDemoExample {
    private static final double[] SLANT_RANGES = ExampleSupport.range(3.0d, 20.0d, 0.25d);

    private ThermalDemoExample() {
    }

    /**
     * 启动示例窗口。
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(ThermalDemoExample::showExample);
    }

    /**
     * 同时展示地爆与空爆两张图。
     */
    private static void showExample() {
        XYChart groundChart = buildChart("total thermal impulse from 1MT groundburst", 0.2d, 110.0d, true);
        XYChart airChart = buildChart("total thermal impulse from 1MT airburst", 0.4d, 375.0d, false);

        groundChart.addSeries("light fog (IVC=3)", SLANT_RANGES, groundThermalSeries(3));
        groundChart.addSeries("smog (IVC=4)", SLANT_RANGES, groundThermalSeries(4));
        groundChart.addSeries("clear air (IVC=7)", SLANT_RANGES, groundThermalSeries(7));

        airChart.addSeries("light fog (IVC=3)", SLANT_RANGES, airThermalSeries(3));
        airChart.addSeries("smog (IVC=4)", SLANT_RANGES, airThermalSeries(4));
        airChart.addSeries("clear air (IVC=7)", SLANT_RANGES, airThermalSeries(7));

        JPanel content = new JPanel(new GridLayout(2, 1));
        content.add(new XChartPanel<>(groundChart));
        content.add(new XChartPanel<>(airChart));
        ExampleSupport.showFrame("Thermal Demo", content, 980, 860);
    }

    /**
     * 创建统一风格的曲线图。
     */
    private static XYChart buildChart(String title, double yMin, double yMax, boolean legendVisible) {
        XYChart chart = new XYChartBuilder()
                .width(900)
                .height(360)
                .title(title)
                .xAxisTitle("slant range (km)")
                .yAxisTitle("total thermal impulse (Cal/cm^2)")
                .build();
        ExampleSupport.applyDarkTheme(chart, ExampleSupport.BLUE, ExampleSupport.RED, ExampleSupport.MAGENTA);
        chart.getStyler().setXAxisMin(3.0d);
        chart.getStyler().setXAxisMax(20.0d);
        chart.getStyler().setYAxisMin(yMin);
        chart.getStyler().setYAxisMax(yMax);
        chart.getStyler().setYAxisLogarithmic(true);
        chart.getStyler().setMarkerSize(0);
        chart.getStyler().setLegendVisible(legendVisible);
        return chart;
    }

    /**
     * 生成地爆热冲量曲线。
     */
    private static double[] groundThermalSeries(int visibilityCode) {
        double[] values = new double[SLANT_RANGES.length];
        BurstParameters burst = new BurstParameters(1000.0d, YieldUnit.KILOTONS, 0.0d, DistanceUnit.KILOMETERS);
        for (int index = 0; index < SLANT_RANGES.length; index++) {
            values[index] = safeGroundThermal(burst, SLANT_RANGES[index], visibilityCode);
        }
        return values;
    }

    /**
     * 生成空爆热冲量曲线。
     */
    private static double[] airThermalSeries(int visibilityCode) {
        double[] values = new double[SLANT_RANGES.length];
        BurstParameters burst = new BurstParameters(1000.0d, YieldUnit.KILOTONS, 70.0d, DistanceUnit.KILOMETERS);
        for (int index = 0; index < SLANT_RANGES.length; index++) {
            values[index] = safeAirThermal(burst, SLANT_RANGES[index], visibilityCode);
        }
        return values;
    }

    /**
     * 安全计算地爆热冲量，越界时返回 0。
     */
    private static double safeGroundThermal(BurstParameters burst, double slantRangeKm, int visibilityCode) {
        try {
            return ThermalCalculator.sovietGroundThermal(burst, slantRangeKm, DistanceUnit.KILOMETERS, visibilityCode);
        } catch (ValueOutsideGraphException exception) {
            return 0.0d;
        }
    }

    /**
     * 安全计算空爆热冲量，越界时返回 0。
     */
    private static double safeAirThermal(BurstParameters burst, double slantRangeKm, int visibilityCode) {
        try {
            return ThermalCalculator.sovietAirThermal(burst, slantRangeKm, DistanceUnit.KILOMETERS, visibilityCode);
        } catch (ValueOutsideGraphException exception) {
            return 0.0d;
        }
    }
}
