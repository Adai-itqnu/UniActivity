package com.example.uniactivity.util;

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
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }
}
