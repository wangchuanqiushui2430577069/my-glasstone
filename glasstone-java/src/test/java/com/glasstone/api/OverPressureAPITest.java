package com.glasstone.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.glasstone.examples.ExampleSupport;
import com.glasstone.model.BurstParameters;
import com.glasstone.overpressure.OverpressureCalculator;
import com.glasstone.units.DistanceUnit;
import com.glasstone.units.PressureUnit;
import com.glasstone.units.UnitConverter;
import com.glasstone.units.YieldUnit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.markers.SeriesMarkers;

/**
 * `OverPressureAPI` 的回归测试。
 */
class OverPressureAPITest {
    private static final Path CHART_DIR = Path.of("target", "test-artifacts", "overpressure-api");

    @Test
    void matchesCurrentPythonReferenceValuesWithDefaultUnits() {
        double[] actual = OverPressureAPI.brodeOverpressureDistances(
                new double[]{1.0d, 2.0d, 5.0d},
                20.0d,
                300.0d
        );

        assertArrayEquals(
                new double[]{788.4071994d, 527.78820855d, 322.61456134d},
                actual,
                1.0e-6d
        );
    }

    @Test
    void matchesScalarBrodeInverseForExplicitUnits() throws IOException {
        double[] targetOps = {2.0d, 3.0d, 3.8d, 4.9d, 7.1d, 8.1d, 10.0d, 12.0d};
        BurstParameters burst = new BurstParameters(20.0d, YieldUnit.KILOTONS, 300.0d, DistanceUnit.METERS);

        double[] actual = OverPressureAPI.brodeOverpressureDistances(
                targetOps,
                20.0d,
                300.0d,
                120,
                YieldUnit.KILOTONS,
                DistanceUnit.METERS,
                PressureUnit.KILOGRAM_PER_CM2
        );

        assertEquals(targetOps.length, actual.length);
        for (int index = 0; index < targetOps.length; index++) {
            assertEquals(
                    OverpressureCalculator.inverseBrodeOverpressure(
                            burst,
                            targetOps[index],
                            PressureUnit.KILOGRAM_PER_CM2,
                            DistanceUnit.METERS,
                            120
                    ),
                    actual[index],
                    1.0e-9d
            );
        }

        saveBrodeRingChart(
                burst,
                targetOps,
                actual,
                DistanceUnit.METERS,
                PressureUnit.KILOGRAM_PER_CM2,
                CHART_DIR.resolve("brode-overpressure-rings-explicit-units")
        );
        assertTrue(Files.exists(CHART_DIR.resolve("brode-overpressure-rings-explicit-units.png")));
    }

    @Test
    void preservesResultsAcrossConvertedUnits() {
        double[] distancesMeters = OverPressureAPI.brodeOverpressureDistances(
                new double[]{1.0d, 2.0d, 5.0d},
                20.0d,
                300.0d,
                100,
                YieldUnit.KILOTONS,
                DistanceUnit.METERS,
                PressureUnit.KILOGRAM_PER_CM2
        );

        double[] distancesFeet = OverPressureAPI.brodeOverpressureDistances(
                new double[]{
                        UnitConverter.convertPressure(1.0d, PressureUnit.KILOGRAM_PER_CM2, PressureUnit.PSI),
                        UnitConverter.convertPressure(2.0d, PressureUnit.KILOGRAM_PER_CM2, PressureUnit.PSI),
                        UnitConverter.convertPressure(5.0d, PressureUnit.KILOGRAM_PER_CM2, PressureUnit.PSI)
                },
                20.0d,
                UnitConverter.convertDistance(300.0d, DistanceUnit.METERS, DistanceUnit.FEET),
                100,
                YieldUnit.KILOTONS,
                DistanceUnit.FEET,
                PressureUnit.PSI
        );

        assertEquals(distancesMeters.length, distancesFeet.length);
        for (int index = 0; index < distancesMeters.length; index++) {
            assertEquals(
                    distancesMeters[index],
                    UnitConverter.convertDistance(distancesFeet[index], DistanceUnit.FEET, DistanceUnit.METERS),
                    1.0e-6d
            );
        }
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> OverPressureAPI.brodeOverpressureDistances(null, 20.0d, 300.0d));
        assertThrows(IllegalArgumentException.class,
                () -> OverPressureAPI.brodeOverpressureDistances(new double[]{0.0d, 1.0d}, 20.0d, 300.0d));
        assertThrows(IllegalArgumentException.class,
                () -> OverPressureAPI.brodeOverpressureDistances(new double[]{1.0d, Double.NaN}, 20.0d, 300.0d));
        assertThrows(IllegalArgumentException.class,
                () -> OverPressureAPI.brodeOverpressureDistances(new double[]{1.0d, 2.0d}, 20.0d, 300.0d, 3));
        assertThrows(IllegalArgumentException.class,
                () -> OverPressureAPI.brodeOverpressureDistances(
                        new double[]{1.0d},
                        0.0d,
                        300.0d,
                        100,
                        YieldUnit.KILOTONS,
                        DistanceUnit.METERS,
                        PressureUnit.KILOGRAM_PER_CM2
                ));
        assertThrows(IllegalArgumentException.class,
                () -> OverPressureAPI.brodeOverpressureDistances(
                        new double[]{1.0d},
                        20.0d,
                        -1.0d,
                        100,
                        YieldUnit.KILOTONS,
                        DistanceUnit.METERS,
                        PressureUnit.KILOGRAM_PER_CM2
                ));
    }

    private static void saveBrodeRingChart(
            BurstParameters burst,
            double[] targetOps,
            double[] inverseDistances,
            DistanceUnit distanceUnit,
            PressureUnit pressureUnit,
            Path output
    ) throws IOException {
        double maxDistance = 0.0d;
        for (double distance : inverseDistances) {
            maxDistance = Math.max(maxDistance, distance);
        }
        double axisLimit = maxDistance * 1.15d;

        XYChart chart = new XYChartBuilder()
                .width(900)
                .height(900)
                .title(String.format(
                        "Brode overpressure rings | yield: %.1f kT | burst height: %.1f m | pressure: %s",
                        UnitConverter.convertYield(burst.yield(), burst.yieldUnit(), YieldUnit.KILOTONS),
                        UnitConverter.convertDistance(burst.burstHeight(), burst.distanceUnit(), DistanceUnit.METERS),
                        pressureUnit.symbol()
                ))
                .xAxisTitle("x (" + distanceUnit.symbol() + ")")
                .yAxisTitle("y (" + distanceUnit.symbol() + ")")
                .build();
        ExampleSupport.applyDarkTheme(
                chart,
                ExampleSupport.DODGER_BLUE,
                ExampleSupport.CYAN,
                ExampleSupport.YELLOW,
                ExampleSupport.GREEN,
                ExampleSupport.MAGENTA,
                ExampleSupport.RED,
                ExampleSupport.DEEP_PINK,
                ExampleSupport.LIGHT_TEXT
        );
        chart.getStyler().setCursorEnabled(false);
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setMarkerSize(5);
        chart.getStyler().setXAxisMin(-axisLimit);
        chart.getStyler().setXAxisMax(axisLimit);
        chart.getStyler().setYAxisMin(-axisLimit);
        chart.getStyler().setYAxisMax(axisLimit);

        for (int index = 0; index < targetOps.length; index++) {
            chart.addSeries(
                            "target " + targetOps[index] + " " + pressureUnit.symbol(),
                            circleXs(inverseDistances[index], 361),
                            circleYs(inverseDistances[index], 361)
                    )
                    .setMarker(SeriesMarkers.NONE);
        }

        chart.addSeries("ground zero", new double[]{0.0d}, new double[]{0.0d})
                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Scatter)
                .setMarker(SeriesMarkers.DIAMOND);

        Files.createDirectories(CHART_DIR);
        BitmapEncoder.saveBitmap(chart, output.toString(), BitmapEncoder.BitmapFormat.PNG);
    }

    private static double[] circleXs(double radius, int pointCount) {
        double[] values = new double[pointCount];
        for (int index = 0; index < pointCount; index++) {
            double angle = (2.0d * Math.PI * index) / (pointCount - 1);
            values[index] = radius * Math.cos(angle);
        }
        return values;
    }

    private static double[] circleYs(double radius, int pointCount) {
        double[] values = new double[pointCount];
        for (int index = 0; index < pointCount; index++) {
            double angle = (2.0d * Math.PI * index) / (pointCount - 1);
            values[index] = radius * Math.sin(angle);
        }
        return values;
    }
}
