package com.glasstone.fallout;

import com.glasstone.model.Coordinate2D;
import com.glasstone.model.WindProfile;
import com.glasstone.units.DistanceUnit;
import com.glasstone.units.YieldUnit;

/**
 * WSEG-10 沉降模型的输入配置。
 *
 * @param groundZero 爆心坐标
 * @param distanceUnit 爆心坐标与爆高采用的距离单位
 * @param yield 爆炸当量数值
 * @param yieldUnit 爆炸当量单位
 * @param fissionFraction 裂变份额
 * @param windProfile 风场输入
 * @param timeOfBurstHours 爆炸发生时刻，单位小时
 */
public record FalloutConfig(
        Coordinate2D groundZero,
        DistanceUnit distanceUnit,
        double yield,
        YieldUnit yieldUnit,
        double fissionFraction,
        WindProfile windProfile,
        double timeOfBurstHours
) {
    /**
     * 校验 WSEG-10 输入配置的完整性和数值范围。
     */
    public FalloutConfig {
        if (groundZero == null) {
            throw new IllegalArgumentException("groundZero must not be null");
        }
        if (distanceUnit == null) {
            throw new IllegalArgumentException("distanceUnit must not be null");
        }
        if (yieldUnit == null) {
            throw new IllegalArgumentException("yieldUnit must not be null");
        }
        if (windProfile == null) {
            throw new IllegalArgumentException("windProfile must not be null");
        }
        if (!Double.isFinite(groundZero.x())) {
            throw new IllegalArgumentException("groundZero.x must be finite");
        }
        if (!Double.isFinite(groundZero.y())) {
            throw new IllegalArgumentException("groundZero.y must be finite");
        }
        if (!Double.isFinite(yield) || yield <= 0.0d) {
            throw new IllegalArgumentException("yield must be positive");
        }
        if (!Double.isFinite(fissionFraction) || fissionFraction < 0.0d || fissionFraction > 1.0d) {
            throw new IllegalArgumentException("fissionFraction must be in [0, 1].");
        }
        if (!Double.isFinite(timeOfBurstHours)) {
            throw new IllegalArgumentException("timeOfBurstHours must be finite");
        }
        if (timeOfBurstHours != 0.0d) {
            throw new IllegalArgumentException("timeOfBurstHours is currently unsupported; only 0 is accepted");
        }
    }
}
