package com.ancientkira.mca.client.gui;

record BlueprintMapViewport(int centerX, int centerY, int left, int top, int right, int bottom,
                            double mapCenterX, double mapCenterZ, float scale) {
    static BlueprintMapViewport create(int centerX, int centerY, int halfSize,
                                       double mapCenterX, double mapCenterZ, float scale) {
        if (scale <= 0.0f) {
            throw new IllegalArgumentException("scale must be positive");
        }
        double originX = Math.rint(centerX - mapCenterX * scale);
        double originZ = Math.rint(centerY - mapCenterZ * scale);
        return new BlueprintMapViewport(
                centerX, centerY, centerX - halfSize, centerY - halfSize, centerX + halfSize, centerY + halfSize,
                (centerX - originX) / scale, (centerY - originZ) / scale, scale
        );
    }

    double screenX(double worldX) {
        return centerX + (worldX - mapCenterX) * scale;
    }

    double screenY(double worldZ) {
        return centerY + (worldZ - mapCenterZ) * scale;
    }

    BlueprintMapFootprint.Cell screenToCell(double screenX, double screenY) {
        int worldX = (int) Math.floor((screenX - centerX) / scale + mapCenterX);
        int worldZ = (int) Math.floor((screenY - centerY) / scale + mapCenterZ);
        return new BlueprintMapFootprint.Cell(worldX, worldZ);
    }

    boolean containsInner(double screenX, double screenY) {
        return screenX >= left + 1 && screenX < right - 1 && screenY >= top + 1 && screenY < bottom - 1;
    }

    int halfSize() {
        return (right - left) / 2;
    }

    ScreenPoint clampMarker(double markerX, double markerY, int markerSize, int edgePadding) {
        double halfMarker = markerSize / 2.0;
        double minX = left + edgePadding + halfMarker;
        double maxX = right - edgePadding - halfMarker;
        double minY = top + edgePadding + halfMarker;
        double maxY = bottom - edgePadding - halfMarker;
        double dx = markerX - centerX;
        double dy = markerY - centerY;
        double factor = 1.0;
        if (markerX < minX || markerX > maxX || markerY < minY || markerY > maxY) {
            double maxDx = Math.min(centerX - minX, maxX - centerX);
            double maxDy = Math.min(centerY - minY, maxY - centerY);
            double xFactor = dx == 0.0 ? Double.POSITIVE_INFINITY : maxDx / Math.abs(dx);
            double yFactor = dy == 0.0 ? Double.POSITIVE_INFINITY : maxDy / Math.abs(dy);
            factor = Math.min(xFactor, yFactor);
        }
        int x = (int) Math.round(centerX + dx * factor);
        int y = (int) Math.round(centerY + dy * factor);
        x = Math.max((int) Math.ceil(minX), Math.min((int) Math.floor(maxX), x));
        y = Math.max((int) Math.ceil(minY), Math.min((int) Math.floor(maxY), y));
        return new ScreenPoint(x, y);
    }

    record ScreenPoint(int x, int y) {
    }
}
