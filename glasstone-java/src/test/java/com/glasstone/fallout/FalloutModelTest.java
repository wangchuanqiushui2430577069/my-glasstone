package com.glasstone.fallout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.glasstone.model.Coordinate2D;
import com.glasstone.model.WindProfile;
import com.glasstone.units.DistanceUnit;
import com.glasstone.units.DoseUnit;
import com.glasstone.units.SpeedUnit;
import com.glasstone.units.UnitConverter;
import com.glasstone.units.WindShearUnit;
import com.glasstone.units.YieldUnit;
import org.junit.jupiter.api.Test;

/**
 * `FalloutModel` 的回归测试。
 */
class FalloutModelTest {
    private static FalloutModel newReferenceModel() {
        return new FalloutModel(new FalloutConfig(
                new Coordinate2D(1.0d, 1.0d),
                DistanceUnit.MILES,
                0.01d,
                YieldUnit.MEGATONS,
                1.0d,
                new WindProfile(1.151515d * 2.0d, SpeedUnit.MILES_PER_HOUR, 225.0d, 0.23d, WindShearUnit.MILES_PER_HOUR_PER_KILOFOOT),
                0.0d
        ));
    }

    @Test
    void matchesCurrentPythonReferenceValues() {
        FalloutModel model = newReferenceModel();

        assertEquals(2356.8738384106623d,
                model.doseRateHPlus1(1.0d, 1.0d, DistanceUnit.MILES, DoseUnit.ROENTGEN),
                1.0e-6);
        assertEquals(788.4293728434772d,
                model.doseRateHPlus1(3.0d, 3.0d, DistanceUnit.MILES, DoseUnit.ROENTGEN),
                1.0e-6);
        assertEquals(201.2282251356861d,
                model.doseRateHPlus1(3.0d, 1.0d, DistanceUnit.MILES, DoseUnit.ROENTGEN),
                1.0e-6);
        assertEquals(6250.113768454942d,
                model.equivalentResidualDose(3.0d, 3.0d, DistanceUnit.MILES, DoseUnit.ROENTGEN),
                1.0e-6);
        assertEquals(1770.894518456919d,
                model.equivalentResidualDose(3.0d, 1.0d, DistanceUnit.MILES, DoseUnit.ROENTGEN),
                1.0e-6);
        assertEquals(62.50113768454942d,
                model.equivalentResidualDose(3.0d, 3.0d, DistanceUnit.MILES, DoseUnit.SIEVERT),
                1.0e-9);
        assertEquals(0.6606975247979251d,
                model.falloutArrivalTimeHours(2.8284271247461903d, DistanceUnit.MILES),
                1.0e-12);
    }

    @Test
    void preservesResultsAcrossConvertedUnits() {
        double groundZeroKm = UnitConverter.convertDistance(1.0d, DistanceUnit.MILES, DistanceUnit.KILOMETERS);
        double pointKm = UnitConverter.convertDistance(3.0d, DistanceUnit.MILES, DistanceUnit.KILOMETERS);
        double windKmPerHour = UnitConverter.convertSpeed(1.151515d * 2.0d, SpeedUnit.MILES_PER_HOUR, SpeedUnit.KILOMETERS_PER_HOUR);
        double shearMetersPerSecondPerKilometer = UnitConverter.convertWindShear(
                0.23d,
                WindShearUnit.MILES_PER_HOUR_PER_KILOFOOT,
                WindShearUnit.METERS_PER_SECOND_PER_KILOMETER
        );

        FalloutModel model = new FalloutModel(new FalloutConfig(
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

        assertEquals(788.4293728434772d,
                model.doseRateHPlus1(pointKm, pointKm, DistanceUnit.KILOMETERS, DoseUnit.ROENTGEN),
                1.0e-4);
        assertEquals(6250.113768454942d,
                model.equivalentResidualDose(pointKm, pointKm, DistanceUnit.KILOMETERS, DoseUnit.ROENTGEN),
                1.0e-3);
    }

    @Test
    void keepsUpwindArrivalAndDoseFinite() {
        FalloutModel model = newReferenceModel();

        for (double[] point : new double[][]{
                {-5.0d, -5.0d},
                {-2.0d, -2.0d},
                {0.0d, 0.0d}
        }) {
            double arrival = model.falloutArrivalTimeHours(point[0], DistanceUnit.MILES);
            double dose = model.equivalentResidualDose(point[0], point[1], DistanceUnit.MILES, DoseUnit.ROENTGEN);
            assertTrue(Double.isFinite(arrival));
            assertTrue(arrival >= 0.5d);
            assertTrue(Double.isFinite(dose));
            assertTrue(dose >= 0.0d);
        }
    }

    @Test
    void acceptsZeroFissionFractionAndReturnsZeroDose() {
        FalloutModel model = new FalloutModel(new FalloutConfig(
                new Coordinate2D(0.0d, 0.0d),
                DistanceUnit.MILES,
                1.0d,
                YieldUnit.KILOTONS,
                0.0d,
                new WindProfile(10.0d, SpeedUnit.MILES_PER_HOUR, 0.0d, 0.23d, WindShearUnit.MILES_PER_HOUR_PER_KILOFOOT),
                0.0d
        ));

        assertEquals(0.0d,
                model.equivalentResidualDose(1.0d, 1.0d, DistanceUnit.MILES, DoseUnit.ROENTGEN),
                0.0d);
    }

    @Test
    void rejectsInvalidConstructorInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> new FalloutConfig(
                        new Coordinate2D(Double.NaN, 0.0d),
                        DistanceUnit.MILES,
                        1.0d,
                        YieldUnit.KILOTONS,
                        1.0d,
                        new WindProfile(10.0d, SpeedUnit.MILES_PER_HOUR, 0.0d, 0.23d, WindShearUnit.MILES_PER_HOUR_PER_KILOFOOT),
                        0.0d
                ));
        assertThrows(IllegalArgumentException.class,
                () -> new FalloutConfig(
                        new Coordinate2D(0.0d, Double.POSITIVE_INFINITY),
                        DistanceUnit.MILES,
                        1.0d,
                        YieldUnit.KILOTONS,
                        1.0d,
                        new WindProfile(10.0d, SpeedUnit.MILES_PER_HOUR, 0.0d, 0.23d, WindShearUnit.MILES_PER_HOUR_PER_KILOFOOT),
                        0.0d
                ));
        assertThrows(IllegalArgumentException.class,
                () -> new FalloutConfig(
                        new Coordinate2D(0.0d, 0.0d),
                        DistanceUnit.MILES,
                        0.0d,
                        YieldUnit.KILOTONS,
                        1.0d,
                        new WindProfile(10.0d, SpeedUnit.MILES_PER_HOUR, 0.0d, 0.23d, WindShearUnit.MILES_PER_HOUR_PER_KILOFOOT),
                        0.0d
                ));
        assertThrows(IllegalArgumentException.class,
                () -> new FalloutConfig(
                        new Coordinate2D(0.0d, 0.0d),
                        DistanceUnit.MILES,
                        1.0d,
                        YieldUnit.KILOTONS,
                        -0.1d,
                        new WindProfile(10.0d, SpeedUnit.MILES_PER_HOUR, 0.0d, 0.23d, WindShearUnit.MILES_PER_HOUR_PER_KILOFOOT),
                        0.0d
                ));
        assertThrows(IllegalArgumentException.class,
                () -> new FalloutConfig(
                        new Coordinate2D(0.0d, 0.0d),
                        DistanceUnit.MILES,
                        1.0d,
                        YieldUnit.KILOTONS,
                        1.1d,
                        new WindProfile(10.0d, SpeedUnit.MILES_PER_HOUR, 0.0d, 0.23d, WindShearUnit.MILES_PER_HOUR_PER_KILOFOOT),
                        0.0d
                ));
        assertThrows(IllegalArgumentException.class,
                () -> new WindProfile(0.0d, SpeedUnit.MILES_PER_HOUR, 0.0d, 0.23d, WindShearUnit.MILES_PER_HOUR_PER_KILOFOOT));
        assertThrows(IllegalArgumentException.class,
                () -> new WindProfile(-5.0d, SpeedUnit.MILES_PER_HOUR, 0.0d, 0.23d, WindShearUnit.MILES_PER_HOUR_PER_KILOFOOT));
        assertThrows(IllegalArgumentException.class,
                () -> new WindProfile(10.0d, SpeedUnit.MILES_PER_HOUR, Double.NaN, 0.23d, WindShearUnit.MILES_PER_HOUR_PER_KILOFOOT));
        assertThrows(IllegalArgumentException.class,
                () -> new WindProfile(10.0d, SpeedUnit.MILES_PER_HOUR, 0.0d, Double.POSITIVE_INFINITY, WindShearUnit.MILES_PER_HOUR_PER_KILOFOOT));
        assertThrows(IllegalArgumentException.class,
                () -> new FalloutConfig(
                        new Coordinate2D(0.0d, 0.0d),
                        DistanceUnit.MILES,
                        1.0d,
                        YieldUnit.KILOTONS,
                        1.0d,
                        new WindProfile(10.0d, SpeedUnit.MILES_PER_HOUR, 0.0d, 0.23d, WindShearUnit.MILES_PER_HOUR_PER_KILOFOOT),
                        1.0d
                ));
        assertThrows(IllegalArgumentException.class,
                () -> new FalloutConfig(
                        new Coordinate2D(0.0d, 0.0d),
                        DistanceUnit.MILES,
                        1.0d,
                        YieldUnit.KILOTONS,
                        1.0d,
                        new WindProfile(10.0d, SpeedUnit.MILES_PER_HOUR, 0.0d, 0.23d, WindShearUnit.MILES_PER_HOUR_PER_KILOFOOT),
                        Double.NaN
                ));
    }

    @Test
    void rejectsNonFiniteQueryCoordinates() {
        FalloutModel model = newReferenceModel();

        assertThrows(IllegalArgumentException.class,
                () -> model.falloutArrivalTimeHours(Double.NaN, DistanceUnit.MILES));
        assertThrows(IllegalArgumentException.class,
                () -> model.falloutArrivalTimeHours(Double.POSITIVE_INFINITY, DistanceUnit.MILES));
        assertThrows(IllegalArgumentException.class,
                () -> model.doseRateHPlus1(Double.NaN, 1.0d, DistanceUnit.MILES, DoseUnit.ROENTGEN));
        assertThrows(IllegalArgumentException.class,
                () -> model.doseRateHPlus1(1.0d, Double.NEGATIVE_INFINITY, DistanceUnit.MILES, DoseUnit.ROENTGEN));
        assertThrows(IllegalArgumentException.class,
                () -> model.equivalentResidualDose(Double.POSITIVE_INFINITY, 1.0d, DistanceUnit.MILES, DoseUnit.ROENTGEN));
        assertThrows(IllegalArgumentException.class,
                () -> model.equivalentResidualDose(1.0d, Double.NaN, DistanceUnit.MILES, DoseUnit.ROENTGEN));
    }

    @Test
    void returnsContourPointsOnIsodoseCurve() {
        FalloutModel model = newReferenceModel();
        Coordinate2D[] points = model.doseContourPoints(1000.0d, 40, DistanceUnit.MILES, DoseUnit.ROENTGEN);

        assertEquals(40, points.length);
        for (int index = 0; index < points.length; index += 10) {
            double dose = model.equivalentResidualDose(points[index].x(), points[index].y(), DistanceUnit.MILES, DoseUnit.ROENTGEN);
            assertTrue(Math.abs(dose - 1000.0d) <= 1000.0d * 1.0e-4d, dose + " != 1000");
        }
    }

    @Test
    void keepsContourPointsConsistentAcrossUnits() {
        FalloutModel model = newReferenceModel();
        Coordinate2D[] contourMiles = model.doseContourPoints(1000.0d, 40, DistanceUnit.MILES, DoseUnit.ROENTGEN);
        Coordinate2D[] contourKilometers = model.doseContourPoints(
                UnitConverter.convertDose(1000.0d, DoseUnit.ROENTGEN, DoseUnit.SIEVERT),
                40,
                DistanceUnit.KILOMETERS,
                DoseUnit.SIEVERT
        );

        assertEquals(contourMiles.length, contourKilometers.length);
        for (int index = 0; index < contourMiles.length; index++) {
            assertEquals(contourMiles[index].x(),
                    UnitConverter.convertDistance(contourKilometers[index].x(), DistanceUnit.KILOMETERS, DistanceUnit.MILES),
                    1.0e-6);
            assertEquals(contourMiles[index].y(),
                    UnitConverter.convertDistance(contourKilometers[index].y(), DistanceUnit.KILOMETERS, DistanceUnit.MILES),
                    1.0e-6);
        }
    }

    @Test
    void contourPointSetsMatchScalarApi() {
        FalloutModel model = newReferenceModel();
        double[] targets = {500.0d, 1000.0d, 3000.0d};
        Coordinate2D[][] contourSets = model.doseContourPointSets(targets, 20, DistanceUnit.MILES, DoseUnit.ROENTGEN);

        assertEquals(targets.length, contourSets.length);
        for (int contourIndex = 0; contourIndex < targets.length; contourIndex++) {
            Coordinate2D[] expected = model.doseContourPoints(targets[contourIndex], 20, DistanceUnit.MILES, DoseUnit.ROENTGEN);
            assertEquals(expected.length, contourSets[contourIndex].length);
            for (int pointIndex = 0; pointIndex < expected.length; pointIndex++) {
                assertEquals(expected[pointIndex].x(), contourSets[contourIndex][pointIndex].x(), 1.0e-9);
                assertEquals(expected[pointIndex].y(), contourSets[contourIndex][pointIndex].y(), 1.0e-9);
            }
        }
    }

    @Test
    void rejectsInvalidContourInputs() {
        FalloutModel model = new FalloutModel(new FalloutConfig(
                new Coordinate2D(0.0d, 0.0d),
                DistanceUnit.MILES,
                1.0d,
                YieldUnit.KILOTONS,
                1.0d,
                new WindProfile(10.0d, SpeedUnit.MILES_PER_HOUR, 0.0d, 0.23d, WindShearUnit.MILES_PER_HOUR_PER_KILOFOOT),
                0.0d
        ));

        assertThrows(IllegalArgumentException.class,
                () -> model.doseContourPoints(0.0d, DistanceUnit.MILES, DoseUnit.ROENTGEN));
        assertThrows(IllegalArgumentException.class,
                () -> model.doseContourPoints(-1.0d, DistanceUnit.MILES, DoseUnit.ROENTGEN));
        assertThrows(IllegalArgumentException.class,
                () -> model.doseContourPoints(Double.NaN, DistanceUnit.MILES, DoseUnit.ROENTGEN));
        assertThrows(IllegalArgumentException.class,
                () -> model.doseContourPoints(1.0d, 3, DistanceUnit.MILES, DoseUnit.ROENTGEN));
        assertThrows(IllegalArgumentException.class,
                () -> model.doseContourPoints(1.0e12d, DistanceUnit.MILES, DoseUnit.ROENTGEN));
        assertThrows(IllegalArgumentException.class,
                () -> model.doseContourPointSets(new double[]{0.0d, 1.0d}, DistanceUnit.MILES, DoseUnit.ROENTGEN));
        assertThrows(IllegalArgumentException.class,
                () -> model.doseContourPointSets(new double[]{1.0d, -1.0d}, DistanceUnit.MILES, DoseUnit.ROENTGEN));
        assertThrows(IllegalArgumentException.class,
                () -> model.doseContourPointSets(new double[]{1.0d, Double.NaN}, DistanceUnit.MILES, DoseUnit.ROENTGEN));
        assertThrows(IllegalArgumentException.class,
                () -> model.doseContourPointSets(new double[]{1.0d, 2.0d}, 3, DistanceUnit.MILES, DoseUnit.ROENTGEN));
        assertThrows(IllegalArgumentException.class,
                () -> model.doseContourPointSets(new double[]{1000.0d, 1.0e12d}, DistanceUnit.MILES, DoseUnit.ROENTGEN));
    }
}
