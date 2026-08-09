package com.example.uniactivity.util;

import com.example.uniactivity.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class GeoUtilsTest {

    @Test
    void rejectsNonFiniteCoordinates() {
        assertThrows(ValidationException.class,
                () -> GeoUtils.haversineMeters(13.7, 109.2, Double.NaN, 109.2));
        assertThrows(ValidationException.class,
                () -> GeoUtils.haversineMeters(13.7, 109.2, 13.7, Double.POSITIVE_INFINITY));
    }

    @Test
    void rejectsCoordinatesOutsideEarthDomains() {
        assertThrows(ValidationException.class,
                () -> GeoUtils.haversineMeters(91, 109.2, 13.7, 109.2));
        assertThrows(ValidationException.class,
                () -> GeoUtils.haversineMeters(13.7, 181, 13.7, 109.2));
    }
}
