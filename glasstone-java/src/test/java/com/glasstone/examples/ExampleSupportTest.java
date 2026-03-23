package com.glasstone.examples;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.knowm.xchart.XChartPanel;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;

/**
 * `ExampleSupport` 的回归测试。
 */
class ExampleSupportTest {
    @Test
    void primeChartLayoutInitializesPlotBounds() throws ReflectiveOperationException {
        XYChart chart = new XYChartBuilder()
                .width(640)
                .height(360)
                .title("test")
                .xAxisTitle("x")
                .yAxisTitle("y")
                .build();
        chart.addSeries("series", new double[]{0.0d, 1.0d}, new double[]{0.0d, 1.0d});
        ExampleSupport.applyDarkTheme(chart, ExampleSupport.BLUE);
        XChartPanel<XYChart> chartPanel = new XChartPanel<>(chart);

        ExampleSupport.primeChartLayout(chartPanel);

        Field plotField = chart.getClass().getSuperclass().getDeclaredField("plot");
        plotField.setAccessible(true);
        Object plot = plotField.get(chart);
        Field boundsField = findField(plot.getClass(), "bounds");
        boundsField.setAccessible(true);
        assertNotNull(boundsField.get(plot));
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
