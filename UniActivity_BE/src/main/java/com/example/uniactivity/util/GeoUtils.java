package com.example.uniactivity.util;

import com.example.uniactivity.exception.ValidationException;

/**
 * Utility class cho tính toán GPS khoảng cách.
 * Sử dụng Haversine formula — độ chính xác cao cho khoảng cách ngắn.
 */
public final class GeoUtils {

    private GeoUtils() {} // Utility class

    /** Bán kính Trái Đất (mét) */
    private static final double EARTH_RADIUS_METERS = 6_371_000;

    /**
     * Tính khoảng cách giữa 2 tọa độ GPS bằng Haversine formula.
     *
     * @param lat1 Latitude điểm 1 (độ)
     * @param lng1 Longitude điểm 1 (độ)
     * @param lat2 Latitude điểm 2 (độ)
     * @param lng2 Longitude điểm 2 (độ)
     * @return Khoảng cách (mét)
     */
    public static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        validateCoordinates(lat1, lng1);
        validateCoordinates(lat2, lng2);
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    private static void validateCoordinates(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90
                || longitude < -180 || longitude > 180) {
            throw new ValidationException("Tọa độ GPS không hợp lệ");
        }
    }
}
