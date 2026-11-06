package com.ancientkira.mca.entity.ai;

/** Low-arc arrow aim, including air drag and gravity instead of a fixed upward offset. */
public final class ArrowTrajectory {
    private ArrowTrajectory() {}
    public static double elevation(double distance, double height, double speed) {
        return elevation(distance, height, speed, .99);
    }
    public static double elevation(double distance, double height, double speed, double drag) {
        if (distance < .001) return Math.atan2(height, .001);
        double low = -1.45;
        double previous = heightAt(distance, speed, low, drag) - height;
        for (int i = 1; i <= 64; i++) {
            double high = -1.45 + i * (2.85 / 64);
            double current = heightAt(distance, speed, high, drag) - height;
            if (Double.isFinite(previous) && Double.isFinite(current) && previous <= 0 && current >= 0) {
                for (int j = 0; j < 24; j++) {
                    double mid = (low + high) * .5;
                    if (heightAt(distance, speed, mid, drag) < height) low = mid;
                    else high = mid;
                }
                return (low + high) * .5;
            }
            low = high; previous = current;
        }
        return Math.atan2(height, distance);
    }
    static double heightAt(double distance, double speed, double angle) {
        return heightAt(distance, speed, angle, .99);
    }
    static double heightAt(double distance, double speed, double angle, double drag) {
        double vx = Math.cos(angle) * speed, vy = Math.sin(angle) * speed;
        double x = 0, y = 0;
        for (int tick = 0; tick < 120; tick++) {
            if (x + vx >= distance) return y + vy * ((distance - x) / vx);
            x += vx; y += vy;
            vx *= drag; vy = vy * drag - .05;
        }
        return Double.NaN;
    }
}
