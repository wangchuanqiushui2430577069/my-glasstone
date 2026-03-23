package com.glasstone.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.glasstone.examples.ExampleSupport;
import com.glasstone.fallout.FalloutConfig;
import com.glasstone.fallout.FalloutModel;
import com.glasstone.model.Coordinate2D;
import com.glasstone.model.WindProfile;
import com.glasstone.units.DistanceUnit;
import com.glasstone.units.DoseUnit;
import com.glasstone.units.SpeedUnit;
import com.glasstone.units.UnitConverter;
import com.glasstone.units.WindShearUnit;
import com.glasstone.units.YieldUnit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.markers.SeriesMarkers;

/**
 * `FalloutAPI` 的回归测试。
 */
class FalloutAPITest {
    private static final Path CHART_DIR = Path.of("target", "test-artifacts", "fallout-api");

    @Test
    void matchesFalloutModelWithPythonDefaultUnits() {
        double groundZeroKm = UnitConverter.convertDistance(1.0d, DistanceUnit.MILES, DistanceUnit.KILOMETERS);
        double windKmPerHour = UnitConverter.convertSpeed(2.30303d, SpeedUnit.MILES_PER_HOUR, SpeedUnit.KILOMETERS_PER_HOUR);
        double shearMetersPerSecondPerKilometer = UnitConverter.convertWindShear(
                0.23d,
                WindShearUnit.MILES_PER_HOUR_PER_KILOFOOT,
                WindShearUnit.METERS_PER_SECOND_PER_KILOMETER
        );
        double[] targetDosesSv = {0.75, 1.5, 3, 5.3, 8.3};

        FalloutModel expectedModel = new FalloutModel(new FalloutConfig(
                new Coordinate2D(groundZeroKm, groundZeroKm),
                DistanceUnit.KILOMETERS,
                10.0d,
                YieldUnit.KILOTONS,
                1.0d,
                new WindProfile(
                        windKmPerHour,
                        SpeedUnit.KILOMETERS_PER_HOUR,
                        225.0d,
                        shearMetersPerSecondPerKilometer,
                        WindShearUnit.METERS_PER_SECOND_PER_KILOMETER
                ),
                0.0d
        ));

        Coordinate2D[][] expected = expectedModel.doseContourPointSets(targetDosesSv, 20, DistanceUnit.KILOMETERS, DoseUnit.SIEVERT);
        Coordinate2D[][] actual = FalloutAPI.doseContourPointSets(
                groundZeroKm,
                groundZeroKm,
                10.0d,
                1.0d,
                windKmPerHour,
                225.0d,
                shearMetersPerSecondPerKilometer,
                targetDosesSv,
                20
        );

        assertContoursEqual(expected, actual, 1.0e-9d);
    }

    @Test
    void supportsExplicitUnits() {
        double[] targetDosesRoentgen = {500.0d, 1000.0d, 3000.0d};
        FalloutModel expectedModel = new FalloutModel(new FalloutConfig(
                new Coordinate2D(1.0d, 1.0d),
                DistanceUnit.MILES,
                0.01d,
                YieldUnit.MEGATONS,
                1.0d,
                new WindProfile(
                        2.30303d,
                        SpeedUnit.MILES_PER_HOUR,
                        225.0d,
                        0.23d,
                        WindShearUnit.MILES_PER_HOUR_PER_KILOFOOT
                ),
                0.0d
        ));

        Coordinate2D[][] expected = expectedModel.doseContourPointSets(targetDosesRoentgen, 40, DistanceUnit.MILES, DoseUnit.ROENTGEN);
        Coordinate2D[][] actual = FalloutAPI.doseContourPointSets(
                1.0d,
                1.0d,
                0.01d,
                1.0d,
                2.30303d,
                225.0d,
                0.23d,
                targetDosesRoentgen,
                40,
                DistanceUnit.MILES,
                DoseUnit.ROENTGEN,
                SpeedUnit.MILES_PER_HOUR,
                WindShearUnit.MILES_PER_HOUR_PER_KILOFOOT,
                YieldUnit.MEGATONS
        );

        assertContoursEqual(expected, actual, 1.0e-9d);
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> FalloutAPI.doseContourPointSets(
                        0.0d,
                        0.0d,
                        1.0d,
                        1.0d,
                        10.0d,
                        0.0d,
                        0.23d,
                        new double[]{0.0d, 1.0d},
                        20,
                        DistanceUnit.MILES,
                        DoseUnit.ROENTGEN,
                        SpeedUnit.MILES_PER_HOUR,
                        WindShearUnit.MILES_PER_HOUR_PER_KILOFOOT,
                        YieldUnit.KILOTONS
                ));
        assertThrows(IllegalArgumentException.class,
                () -> FalloutAPI.doseContourPointSets(
                        0.0d,
                        0.0d,
                        1.0d,
                        1.0d,
                        10.0d,
                        0.0d,
                        0.23d,
                        new double[]{1.0d, 2.0d},
                        3,
                        DistanceUnit.MILES,
                        DoseUnit.ROENTGEN,
                        SpeedUnit.MILES_PER_HOUR,
                        WindShearUnit.MILES_PER_HOUR_PER_KILOFOOT,
                        YieldUnit.KILOTONS
                ));
        assertThrows(IllegalArgumentException.class,
                () -> FalloutAPI.doseContourPointSets(
                        0.0d,
                        0.0d,
                        1.0d,
                        1.0d,
                        10.0d,
                        0.0d,
                        0.23d,
                        1.0d,
                        new double[]{1000.0d},
                        20,
                        DistanceUnit.MILES,
                        DoseUnit.ROENTGEN,
                        SpeedUnit.MILES_PER_HOUR,
                        WindShearUnit.MILES_PER_HOUR_PER_KILOFOOT,
                        YieldUnit.KILOTONS
                ));
    }

    @Test
    void rendersContourPointSetsToPng() throws IOException {
        double[] targetDosesRoentgen = {0.75, 1.5, 3, 5.3, 8.3};
        Coordinate2D[][] contoursMiles = FalloutAPI.doseContourPointSets(
                1.0d,
                1.0d,
                0.01d,
                1.0d,
                2.30303d,
                225.0d,
                0.23d,
                targetDosesRoentgen,
                80,
                DistanceUnit.MILES,
                DoseUnit.SIEVERT,
                SpeedUnit.MILES_PER_HOUR,
                WindShearUnit.MILES_PER_HOUR_PER_KILOFOOT,
                YieldUnit.MEGATONS
        );
        double groundZeroMeters = UnitConverter.convertDistance(1.0d, DistanceUnit.MILES, DistanceUnit.METERS);
        Coordinate2D[][] contoursMeters = FalloutAPI.doseContourPointSets(
                groundZeroMeters,
                groundZeroMeters,
                0.01d,
                1.0d,
                2.30303d,
                225.0d,
                0.23d,
                targetDosesRoentgen,
                80,
                DistanceUnit.METERS,
                DoseUnit.SIEVERT,
                SpeedUnit.MILES_PER_HOUR,
                WindShearUnit.MILES_PER_HOUR_PER_KILOFOOT,
                YieldUnit.MEGATONS
        );

        assertContoursEqual(
                contoursMiles,
                convertContours(contoursMeters, DistanceUnit.METERS, DistanceUnit.MILES),
                1.0e-6d
        );

        saveContourChart(
                contoursMiles,
                1.0d,
                1.0d,
                DistanceUnit.MILES,
                targetDosesRoentgen,
                DoseUnit.SIEVERT,
                CHART_DIR.resolve("dose-contour-point-sets-miles")
        );
        saveContourChart(
                contoursMeters,
                groundZeroMeters,
                groundZeroMeters,
                DistanceUnit.METERS,
                targetDosesRoentgen,
                DoseUnit.SIEVERT,
                CHART_DIR.resolve("dose-contour-point-sets-meters")
        );

        assertTrue(Files.exists(CHART_DIR.resolve("dose-contour-point-sets-miles.png")));
        assertTrue(Files.exists(CHART_DIR.resolve("dose-contour-point-sets-meters.png")));
    }

    private static void saveContourChart(
            Coordinate2D[][] contours,
            double groundZeroX,
            double groundZeroY,
            DistanceUnit distanceUnit,
            double[] targetDoses,
            DoseUnit doseUnit,
            Path output
    ) throws IOException {
        AxisBounds bounds = axisBounds(contours, groundZeroX, groundZeroY);
        XYChart chart = new XYChartBuilder()
                .width(1100)
                .height(850)
                .title("WSEG-10 dose contour point sets (" + distanceUnit.symbol() + ")")
                .xAxisTitle("x (" + distanceUnit.symbol() + ")")
                .yAxisTitle("y (" + distanceUnit.symbol() + ")")
                .build();
        ExampleSupport.applyDarkTheme(
                chart,
                ExampleSupport.YELLOW,
                ExampleSupport.CYAN,
                ExampleSupport.MAGENTA,
                ExampleSupport.RED
        );
        // BitmapEncoder 走的是离屏绘制路径，XChart 3.8.8 在该路径下不会初始化 cursor。
        chart.getStyler().setCursorEnabled(false);
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setMarkerSize(6);
        chart.getStyler().setXAxisMin(bounds.minX());
        chart.getStyler().setXAxisMax(bounds.maxX());
        chart.getStyler().setYAxisMin(bounds.minY());
        chart.getStyler().setYAxisMax(bounds.maxY());

        for (int contourIndex = 0; contourIndex < contours.length; contourIndex++) {
            Coordinate2D[] contour = contours[contourIndex];
            XYSeries series = chart.addSeries(
                    formatDoseLabel(targetDoses[contourIndex], doseUnit),
                    closedXs(contour),
                    closedYs(contour)
            );
            series.setMarker(SeriesMarkers.NONE);
        }

        chart.addSeries("ground zero", new double[]{groundZeroX}, new double[]{groundZeroY})
                .setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Scatter)
                .setMarker(SeriesMarkers.DIAMOND);

        Files.createDirectories(CHART_DIR);
        BitmapEncoder.saveBitmap(chart, output.toString(), BitmapEncoder.BitmapFormat.PNG);
    }

    private static void assertContoursEqual(Coordinate2D[][] expected, Coordinate2D[][] actual, double tolerance) {
        assertEquals(expected.length, actual.length);
        for (int contourIndex = 0; contourIndex < expected.length; contourIndex++) {
            assertEquals(expected[contourIndex].length, actual[contourIndex].length);
            for (int pointIndex = 0; pointIndex < expected[contourIndex].length; pointIndex++) {
                assertEquals(expected[contourIndex][pointIndex].x(), actual[contourIndex][pointIndex].x(), tolerance);
                assertEquals(expected[contourIndex][pointIndex].y(), actual[contourIndex][pointIndex].y(), tolerance);
            }
        }
    }

    private static Coordinate2D[][] convertContours(
            Coordinate2D[][] contours,
            DistanceUnit sourceUnit,
            DistanceUnit targetUnit
    ) {
        Coordinate2D[][] converted = new Coordinate2D[contours.length][];
        for (int contourIndex = 0; contourIndex < contours.length; contourIndex++) {
            converted[contourIndex] = new Coordinate2D[contours[contourIndex].length];
            for (int pointIndex = 0; pointIndex < contours[contourIndex].length; pointIndex++) {
                Coordinate2D point = contours[contourIndex][pointIndex];
                converted[contourIndex][pointIndex] = new Coordinate2D(
                        UnitConverter.convertDistance(point.x(), sourceUnit, targetUnit),
                        UnitConverter.convertDistance(point.y(), sourceUnit, targetUnit)
                );
            }
        }
        return converted;
    }

    private static AxisBounds axisBounds(Coordinate2D[][] contours, double groundZeroX, double groundZeroY) {
        double minX = groundZeroX;
        double maxX = groundZeroX;
        double minY = groundZeroY;
        double maxY = groundZeroY;
        for (Coordinate2D[] contour : contours) {
            for (Coordinate2D point : contour) {
                minX = Math.min(minX, point.x());
                maxX = Math.max(maxX, point.x());
                minY = Math.min(minY, point.y());
                maxY = Math.max(maxY, point.y());
            }
        }
        double paddingX = axisPadding(minX, maxX);
        double paddingY = axisPadding(minY, maxY);
        return new AxisBounds(
                minX - paddingX,
                maxX + paddingX,
                minY - paddingY,
                maxY + paddingY
        );
    }

    private static double axisPadding(double min, double max) {
        double span = max - min;
        if (span <= 0.0d) {
            return Math.max(Math.abs(min), 1.0d) * 0.1d;
        }
        return span * 0.1d;
    }

    private static String formatDoseLabel(double targetDose, DoseUnit doseUnit) {
        long rounded = Math.round(targetDose);
        if (Math.abs(targetDose - rounded) <= 1.0e-9d) {
            return rounded + " " + doseUnit.symbol();
        }
        return String.format(Locale.ROOT, "%.3f %s", targetDose, doseUnit.symbol());
    }

    private static double[] closedXs(Coordinate2D[] contour) {
        double[] xs = new double[contour.length + 1];
        for (int index = 0; index < contour.length; index++) {
            xs[index] = contour[index].x();
        }
        xs[contour.length] = contour[0].x();
        return xs;
    }

    private static double[] closedYs(Coordinate2D[] contour) {
        double[] ys = new double[contour.length + 1];
        for (int index = 0; index < contour.length; index++) {
            ys[index] = contour[index].y();
        }
        ys[contour.length] = contour[0].y();
        return ys;
    }

    private record AxisBounds(double minX, double maxX, double minY, double maxY) {
    }
}
