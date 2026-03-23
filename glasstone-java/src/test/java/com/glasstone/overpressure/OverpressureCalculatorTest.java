package com.glasstone.overpressure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.glasstone.model.BurstParameters;
import com.glasstone.units.DistanceUnit;
import com.glasstone.units.PressureUnit;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import org.junit.jupiter.api.Test;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.markers.SeriesMarkers;

/**
 * `OverpressureCalculator` 的回归测试。
 */
class OverpressureCalculatorTest {
    private static final Path INVERSE_CHART_DIR = Path.of(
            "d:/Downloads/glasstone-master/glasstone-java/target/test-artifacts/overpressure-inverse"
    );

    private static double[] range(double startInclusive, double endInclusive, int pointCount) {
        double[] values = new double[pointCount];
        double step = (endInclusive - startInclusive) / (pointCount - 1);
        for (int index = 0; index < pointCount; index++) {
            values[index] = startInclusive + (index * step);
        }
        return values;
    }

    private static double[] values(double[] xs, DoubleUnaryOperator operator) {
        double[] ys = new double[xs.length];
        for (int index = 0; index < xs.length; index++) {
            ys[index] = operator.applyAsDouble(xs[index]);
        }
        return ys;
    }

    private static double[] constantValues(int pointCount, double value) {
        double[] values = new double[pointCount];
        for (int index = 0; index < pointCount; index++) {
            values[index] = value;
        }
        return values;
    }

    private static void saveChart(
            String fileName,
            String title,
            String seriesName,
            double[] xs,
            double[] ys,
            double targetValue,
            List<Double> inverseRanges
    ) throws IOException {
        XYChart chart = new XYChartBuilder()
                .width(1100)
                .height(720)
                .title(title)
                .xAxisTitle("ground range (m)")
                .yAxisTitle("pressure (kg/cm^2)")
                .build();
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setMarkerSize(7);
        chart.getStyler().setYAxisLogarithmic(true);
        chart.getStyler().setPlotGridLinesVisible(true);
        chart.getStyler().setSeriesColors(new Color[]{
                new Color(0x1F77B4),
                new Color(0xD62728),
                new Color(0x2CA02C)
        });

        chart.addSeries(seriesName, xs, ys).setMarker(SeriesMarkers.NONE);
        chart.addSeries("target", xs, constantValues(xs.length, targetValue)).setMarker(SeriesMarkers.NONE);

        List<Double> markerYs = new ArrayList<>(inverseRanges.size());
        for (double inverseRange : inverseRanges) {
            markerYs.add(targetValue);
        }
        chart.addSeries(
                        "inverse roots",
                        inverseRanges,
                        markerYs
                )
                .setXYSeriesRenderStyle(org.knowm.xchart.XYSeries.XYSeriesRenderStyle.Scatter)
                .setMarker(SeriesMarkers.DIAMOND);

        Files.createDirectories(INVERSE_CHART_DIR);
        BitmapEncoder.saveBitmap(chart, INVERSE_CHART_DIR.resolve(fileName).toString(), BitmapEncoder.BitmapFormat.PNG);
    }

    // 验证 Brode 和 DNA 模型结果与 Python 参考值一致。
    @Test
    void matchesBrodeAndDnaReferenceValues() {
        BurstParameters burst = new BurstParameters(100.0d, com.glasstone.units.YieldUnit.KILOTONS, 500.0d, DistanceUnit.METERS);

        assertEquals(0.8302285824881421d,
                OverpressureCalculator.brodeOverpressure(burst, 1500.0d, DistanceUnit.METERS, PressureUnit.KILOGRAM_PER_CM2),
                1.0e-12);
        assertEquals(0.8276451701031705d,
                OverpressureCalculator.dnaStaticOverpressure(burst, 1500.0d, DistanceUnit.METERS, PressureUnit.KILOGRAM_PER_CM2),
                1.0e-12);
        assertEquals(0.20999873261808494d,
                OverpressureCalculator.dnaDynamicPressure(burst, 1500.0d, DistanceUnit.METERS, PressureUnit.KILOGRAM_PER_CM2),
                1.0e-12);
    }

    // 验证 Brode、DNA 静压和 DNA 动压数值反解可以回到参考地面距离。
    @Test
    void numericallyInvertsBrodeAndDnaReferenceValues() {
        BurstParameters burst = new BurstParameters(100.0d, com.glasstone.units.YieldUnit.KILOTONS, 500.0d, DistanceUnit.METERS);

        assertEquals(1500.0d,
                OverpressureCalculator.inverseBrodeOverpressure(
                        burst,
                        0.8302285824881421d,
                        PressureUnit.KILOGRAM_PER_CM2,
                        DistanceUnit.METERS
                ),
                1.0e-6);
        assertEquals(1500.0d,
                OverpressureCalculator.inverseDnaStaticOverpressure(
                        burst,
                        0.8276451701031705d,
                        PressureUnit.KILOGRAM_PER_CM2,
                        DistanceUnit.METERS
                ),
                1.0e-6);
        assertEquals(1500.0d,
                OverpressureCalculator.inverseDnaDynamicPressure(
                        burst,
                        0.20999873261808494d,
                        PressureUnit.KILOGRAM_PER_CM2,
                        DistanceUnit.METERS
                ),
                1.0e-6);
    }

    // 验证 Brode 在局部双解情况下返回离爆点最近的那条解。
    @Test
    void selectsInnermostBrodeRootWhenTwoSolutionsExist() {
        BurstParameters burst = new BurstParameters(100.0d, com.glasstone.units.YieldUnit.KILOTONS, 2000.0d, DistanceUnit.METERS);

        assertEquals(12.44218854755921d,
                OverpressureCalculator.inverseBrodeOverpressure(
                        burst,
                        0.6165580544869718d,
                        PressureUnit.KILOGRAM_PER_CM2,
                        DistanceUnit.METERS
                ),
                0.05d);
    }

    // 验证苏联超压模型正算和反算结果与 Python 参考值一致。
    @Test
    void matchesSovietReferenceValues() {
        BurstParameters burst = new BurstParameters(20.0d, com.glasstone.units.YieldUnit.KILOTONS, 300.0d, DistanceUnit.METERS);

        assertEquals(0.3026958213247551d,
                OverpressureCalculator.sovietOverpressure(burst, 1500.0d, DistanceUnit.METERS, true, PressureUnit.KILOGRAM_PER_CM2),
                1.0e-12);
        assertEquals(0.3478123752492143d,
                OverpressureCalculator.sovietOverpressure(burst, 1500.0d, DistanceUnit.METERS, false, PressureUnit.KILOGRAM_PER_CM2),
                1.0e-12);
        assertEquals(1018.9198711274659d,
                OverpressureCalculator.inverseSovietOverpressure(burst, 0.5d, PressureUnit.KILOGRAM_PER_CM2, true, DistanceUnit.METERS),
                1.0e-9);
        assertEquals(1205.1277937411498d,
                OverpressureCalculator.inverseSovietOverpressure(burst, 0.5d, PressureUnit.KILOGRAM_PER_CM2, false, DistanceUnit.METERS),
                1.0e-9);
    }

    // 验证在不同当量/距离单位下超压结果保持一致。
    @Test
    void preservesOverpressureAcrossConvertedUnits() {
        BurstParameters nativeBurst = new BurstParameters(20.0d, com.glasstone.units.YieldUnit.KILOTONS, 300.0d, DistanceUnit.METERS);
        BurstParameters convertedBurst = new BurstParameters(0.02d, com.glasstone.units.YieldUnit.MEGATONS, 0.3d, DistanceUnit.KILOMETERS);

        double nativeThermal = OverpressureCalculator.sovietOverpressure(
                nativeBurst, 1500.0d, DistanceUnit.METERS, true, PressureUnit.KILOGRAM_PER_CM2);
        double convertedThermal = OverpressureCalculator.sovietOverpressure(
                convertedBurst, 1.5d, DistanceUnit.KILOMETERS, true, PressureUnit.KILOGRAM_PER_CM2);
        double nativeInverse = OverpressureCalculator.inverseSovietOverpressure(
                nativeBurst, 0.5d, PressureUnit.KILOGRAM_PER_CM2, true, DistanceUnit.METERS);
        double convertedInverseKm = OverpressureCalculator.inverseSovietOverpressure(
                convertedBurst, 0.5d, PressureUnit.KILOGRAM_PER_CM2, true, DistanceUnit.KILOMETERS);

        assertEquals(nativeThermal, convertedThermal, 1.0e-12);
        assertEquals(nativeInverse / 1000.0d, convertedInverseKm, 1.0e-12);
    }

    // 验证 DNA 动压在多解场景下默认返回离爆点最近的那条解。
    @Test
    void selectsInnermostDnaDynamicPressureDistance() {
        BurstParameters burst = new BurstParameters(100.0d, com.glasstone.units.YieldUnit.KILOTONS, 2000.0d, DistanceUnit.METERS);
        double innerRadiusMeters = OverpressureCalculator.inverseDnaDynamicPressure(
                burst,
                0.02d,
                PressureUnit.KILOGRAM_PER_CM2,
                DistanceUnit.METERS
        );

        assertEquals(909.4632264966992d, innerRadiusMeters, 2.0d);
    }

    // 导出反解结果示意图，便于直观看到目标值与反解交点。
    @Test
    void rendersInverseResultCharts() throws IOException {
        BurstParameters brodeBurst = new BurstParameters(100.0d, com.glasstone.units.YieldUnit.KILOTONS, 500.0d, DistanceUnit.METERS);
        double brodeTarget = 0.8302285824881421d;
        double[] brodeXs = range(100.0d, 4000.0d, 240);
        saveChart(
                "brode-inverse-reference",
                "Brode inverse overpressure reference",
                "Brode static overpressure",
                brodeXs,
                values(brodeXs, range -> OverpressureCalculator.brodeOverpressure(
                        brodeBurst,
                        range,
                        DistanceUnit.METERS,
                        PressureUnit.KILOGRAM_PER_CM2
                )),
                brodeTarget,
                List.of(OverpressureCalculator.inverseBrodeOverpressure(
                        brodeBurst,
                        brodeTarget,
                        PressureUnit.KILOGRAM_PER_CM2,
                        DistanceUnit.METERS
                ))
        );

        BurstParameters dnaStaticBurst = new BurstParameters(100.0d, com.glasstone.units.YieldUnit.KILOTONS, 500.0d, DistanceUnit.METERS);
        double dnaStaticTarget = 0.8276451701031705d;
        double[] dnaStaticXs = range(100.0d, 4000.0d, 240);
        saveChart(
                "dna-static-inverse-reference",
                "DNA static inverse overpressure reference",
                "DNA static overpressure",
                dnaStaticXs,
                values(dnaStaticXs, range -> OverpressureCalculator.dnaStaticOverpressure(
                        dnaStaticBurst,
                        range,
                        DistanceUnit.METERS,
                        PressureUnit.KILOGRAM_PER_CM2
                )),
                dnaStaticTarget,
                List.of(OverpressureCalculator.inverseDnaStaticOverpressure(
                        dnaStaticBurst,
                        dnaStaticTarget,
                        PressureUnit.KILOGRAM_PER_CM2,
                        DistanceUnit.METERS
                ))
        );

        BurstParameters sovietBurst = new BurstParameters(20.0d, com.glasstone.units.YieldUnit.KILOTONS, 300.0d, DistanceUnit.METERS);
        double sovietTarget = 0.5d;
        double[] sovietXs = range(200.0d, 2500.0d, 240);
        XYChart sovietChart = new XYChartBuilder()
                .width(1100)
                .height(720)
                .title("Soviet inverse overpressure reference")
                .xAxisTitle("ground range (m)")
                .yAxisTitle("pressure (kg/cm^2)")
                .build();
        sovietChart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        sovietChart.getStyler().setMarkerSize(7);
        sovietChart.getStyler().setYAxisLogarithmic(true);
        sovietChart.getStyler().setSeriesColors(new Color[]{
                new Color(0xD62728),
                new Color(0x1F77B4),
                new Color(0x2CA02C),
                new Color(0x9467BD),
                new Color(0x8C564B)
        });
        sovietChart.addSeries(
                        "Soviet thermal-layer",
                        sovietXs,
                        values(sovietXs, range -> OverpressureCalculator.sovietOverpressure(
                                sovietBurst,
                                range,
                                DistanceUnit.METERS,
                                true,
                                PressureUnit.KILOGRAM_PER_CM2
                        ))
                )
                .setMarker(SeriesMarkers.NONE);
        sovietChart.addSeries(
                        "Soviet mach",
                        sovietXs,
                        values(sovietXs, range -> OverpressureCalculator.sovietOverpressure(
                                sovietBurst,
                                range,
                                DistanceUnit.METERS,
                                false,
                                PressureUnit.KILOGRAM_PER_CM2
                        ))
                )
                .setMarker(SeriesMarkers.NONE);
        sovietChart.addSeries("target", sovietXs, constantValues(sovietXs.length, sovietTarget)).setMarker(SeriesMarkers.NONE);
        sovietChart.addSeries(
                        "thermal inverse",
                        List.of(OverpressureCalculator.inverseSovietOverpressure(
                                sovietBurst,
                                sovietTarget,
                                PressureUnit.KILOGRAM_PER_CM2,
                                true,
                                DistanceUnit.METERS
                        )),
                        List.of(sovietTarget)
                )
                .setXYSeriesRenderStyle(org.knowm.xchart.XYSeries.XYSeriesRenderStyle.Scatter)
                .setMarker(SeriesMarkers.DIAMOND);
        sovietChart.addSeries(
                        "mach inverse",
                        List.of(OverpressureCalculator.inverseSovietOverpressure(
                                sovietBurst,
                                sovietTarget,
                                PressureUnit.KILOGRAM_PER_CM2,
                                false,
                                DistanceUnit.METERS
                        )),
                        List.of(sovietTarget)
                )
                .setXYSeriesRenderStyle(org.knowm.xchart.XYSeries.XYSeriesRenderStyle.Scatter)
                .setMarker(SeriesMarkers.CIRCLE);
        Files.createDirectories(INVERSE_CHART_DIR);
        BitmapEncoder.saveBitmap(
                sovietChart,
                INVERSE_CHART_DIR.resolve("soviet-inverse-reference").toString(),
                BitmapEncoder.BitmapFormat.PNG
        );

        BurstParameters dnaDynamicBurst = new BurstParameters(100.0d, com.glasstone.units.YieldUnit.KILOTONS, 500.0d, DistanceUnit.METERS);
        double dnaDynamicTarget = 0.20999873261808494d;
        double[] dnaDynamicXs = range(100.0d, 4000.0d, 240);
        saveChart(
                "dna-dynamic-inverse-reference",
                "DNA dynamic inverse reference",
                "DNA dynamic pressure",
                dnaDynamicXs,
                values(dnaDynamicXs, range -> OverpressureCalculator.dnaDynamicPressure(
                        dnaDynamicBurst,
                        range,
                        DistanceUnit.METERS,
                        PressureUnit.KILOGRAM_PER_CM2
                )),
                dnaDynamicTarget,
                List.of(OverpressureCalculator.inverseDnaDynamicPressure(
                        dnaDynamicBurst,
                        dnaDynamicTarget,
                        PressureUnit.KILOGRAM_PER_CM2,
                        DistanceUnit.METERS
                ))
        );

        BurstParameters dnaDynamicMultiBurst = new BurstParameters(100.0d, com.glasstone.units.YieldUnit.KILOTONS, 2000.0d, DistanceUnit.METERS);
        double dnaDynamicMultiTarget = 0.02d;
        double[] dnaDynamicMultiXs = range(100.0d, 8000.0d, 320);
        XYChart dnaDynamicMultiChart = new XYChartBuilder()
                .width(1100)
                .height(720)
                .title("DNA dynamic inverse outer-distance reference")
                .xAxisTitle("ground range (m)")
                .yAxisTitle("pressure (kg/cm^2)")
                .build();
        dnaDynamicMultiChart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        dnaDynamicMultiChart.getStyler().setMarkerSize(7);
        dnaDynamicMultiChart.getStyler().setYAxisLogarithmic(true);
        dnaDynamicMultiChart.getStyler().setSeriesColors(new Color[]{
                new Color(0x1F77B4),
                new Color(0xD62728),
                new Color(0x2CA02C),
                new Color(0x9467BD)
        });
        dnaDynamicMultiChart.addSeries(
                        "DNA dynamic pressure",
                        dnaDynamicMultiXs,
                        values(dnaDynamicMultiXs, range -> OverpressureCalculator.dnaDynamicPressure(
                                dnaDynamicMultiBurst,
                                range,
                                DistanceUnit.METERS,
                                PressureUnit.KILOGRAM_PER_CM2
                        ))
                )
                .setMarker(SeriesMarkers.NONE);
        dnaDynamicMultiChart.addSeries(
                        "target",
                        dnaDynamicMultiXs,
                        constantValues(dnaDynamicMultiXs.length, dnaDynamicMultiTarget)
                )
                .setMarker(SeriesMarkers.NONE);
        dnaDynamicMultiChart.addSeries(
                        "selected inverse distance",
                        List.of(OverpressureCalculator.inverseDnaDynamicPressure(
                                dnaDynamicMultiBurst,
                                dnaDynamicMultiTarget,
                                PressureUnit.KILOGRAM_PER_CM2,
                                DistanceUnit.METERS
                        )),
                        List.of(dnaDynamicMultiTarget)
                )
                .setXYSeriesRenderStyle(org.knowm.xchart.XYSeries.XYSeriesRenderStyle.Scatter)
                .setMarker(SeriesMarkers.CIRCLE);
        BitmapEncoder.saveBitmap(
                dnaDynamicMultiChart,
                INVERSE_CHART_DIR.resolve("dna-dynamic-inverse-multiroot").toString(),
                BitmapEncoder.BitmapFormat.PNG
        );

        assertTrue(Files.exists(INVERSE_CHART_DIR.resolve("brode-inverse-reference.png")));
        assertTrue(Files.exists(INVERSE_CHART_DIR.resolve("dna-static-inverse-reference.png")));
        assertTrue(Files.exists(INVERSE_CHART_DIR.resolve("soviet-inverse-reference.png")));
        assertTrue(Files.exists(INVERSE_CHART_DIR.resolve("dna-dynamic-inverse-reference.png")));
        assertTrue(Files.exists(INVERSE_CHART_DIR.resolve("dna-dynamic-inverse-multiroot.png")));
    }
}
