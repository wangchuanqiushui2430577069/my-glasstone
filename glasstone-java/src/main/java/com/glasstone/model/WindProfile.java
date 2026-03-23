package com.glasstone.model;

import com.glasstone.units.SpeedUnit;
import com.glasstone.units.WindShearUnit;

/**
 * 描述 WSEG-10 等模型所需的风场输入。
 *
 * @param speed 风速数值
 * @param speedUnit 风速单位
 * @param directionDegrees 风向角度，约定北风来向为 0 度
 * @param shear 风切变数值
 * @param shearUnit 风切变单位
 */
public record WindProfile(
        double speed,
        SpeedUnit speedUnit,
        double directionDegrees,
        double shear,
        WindShearUnit shearUnit
) {
    public WindProfile {
        if (speedUnit == null) {
            throw new IllegalArgumentException("speedUnit must not be null");
        }
        if (shearUnit == null) {
            throw new IllegalArgumentException("shearUnit must not be null");
        }
        if (!Double.isFinite(speed) || speed <= 0.0d) {
            throw new IllegalArgumentException("speed must be positive");
        }
        if (!Double.isFinite(directionDegrees)) {
            throw new IllegalArgumentException("directionDegrees must be finite");
        }
        if (!Double.isFinite(shear)) {
            throw new IllegalArgumentException("shear must be finite");
        }
    }
}
