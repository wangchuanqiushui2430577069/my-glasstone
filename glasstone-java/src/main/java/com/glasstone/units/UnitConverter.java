package com.glasstone.units;

import com.glasstone.exception.UnknownUnitException;

/**
 * 统一封装库内使用的单位换算。
 */
public final class UnitConverter {
    /**
     * `psi -> kg/cm^2` 的换算系数。
     */
    private static final double PSI_TO_KG_PER_CM2 = 0.070307d;
    /**
     * `psi -> MPa` 的换算系数。
     */
    private static final double PSI_TO_MPA = 1.0d / 145.037738d;
    /**
     * `m/s/km -> mph/kilofoot` 的换算系数。
     */
    private static final double MPH_PER_KILOFOOT_PER_MPS_PER_KM = 0.13625756613945836d;

    /**
     * 私有构造器，禁止实例化工具类。
     */
    private UnitConverter() {
    }

    /**
     * 换算核爆当量单位。
     *
     * @param value 原始当量值
     * @param from 原单位
     * @param to 目标单位
     * @return 换算后的当量值
     */
    public static double convertYield(double value, YieldUnit from, YieldUnit to) {
        if (from == to) {
            return value;
        }
        return switch (from) {
            case KILOTONS -> switch (to) {
                case MEGATONS -> value / 1000.0d;
                default -> throw unsupported(from, to);
            };
            case MEGATONS -> switch (to) {
                case KILOTONS -> value * 1000.0d;
                default -> throw unsupported(from, to);
            };
        };
    }

    /**
     * 换算距离单位。
     *
     * @param value 原始距离值
     * @param from 原单位
     * @param to 目标单位
     * @return 换算后的距离值
     */
    public static double convertDistance(double value, DistanceUnit from, DistanceUnit to) {
        if (from == to) {
            return value;
        }
        // 先统一换算到米，再从米换算到目标单位。
        double meters = switch (from) {
            case METERS -> value;
            case KILOMETERS -> value * 1000.0d;
            case KILOFEET -> value * 304.8d;
            case MILES -> value * 1609.34d;
            case YARDS -> value * 0.9144d;
            case FEET -> value * 0.3048d;
        };
        return switch (to) {
            case METERS -> meters;
            case KILOMETERS -> meters / 1000.0d;
            case KILOFEET -> meters / 304.8d;
            case MILES -> meters / 1609.34d;
            case YARDS -> meters / 0.9144d;
            case FEET -> meters / 0.3048d;
        };
    }

    /**
     * 换算压力单位。
     *
     * @param value 原始压力值
     * @param from 原单位
     * @param to 目标单位
     * @return 换算后的压力值
     */
    public static double convertPressure(double value, PressureUnit from, PressureUnit to) {
        if (from == to) {
            return value;
        }
        // 先转换成 psi，再映射到目标压力单位。
        double psi = switch (from) {
            case PSI -> value;
            case KILOGRAM_PER_CM2 -> value / PSI_TO_KG_PER_CM2;
            case MEGAPASCAL -> value / PSI_TO_MPA;
            case PASCAL -> (value / 1_000_000.0d) / PSI_TO_MPA;
        };
        return switch (to) {
            case PSI -> psi;
            case KILOGRAM_PER_CM2 -> psi * PSI_TO_KG_PER_CM2;
            case MEGAPASCAL -> psi * PSI_TO_MPA;
            case PASCAL -> psi * PSI_TO_MPA * 1_000_000.0d;
        };
    }

    /**
     * 换算速度单位。
     *
     * @param value 原始速度值
     * @param from 原单位
     * @param to 目标单位
     * @return 换算后的速度值
     */
    public static double convertSpeed(double value, SpeedUnit from, SpeedUnit to) {
        if (from == to) {
            return value;
        }
        // 统一以米每秒作为内部中间单位。
        double metersPerSecond = switch (from) {
            case METERS_PER_SECOND -> value;
            case MILES_PER_HOUR -> value / 2.23694d;
            case KILOMETERS_PER_HOUR -> value / 3.6d;
        };
        return switch (to) {
            case METERS_PER_SECOND -> metersPerSecond;
            case MILES_PER_HOUR -> metersPerSecond * 2.23694d;
            case KILOMETERS_PER_HOUR -> metersPerSecond * 3.6d;
        };
    }

    /**
     * 换算风切变单位。
     *
     * @param value 原始风切变值
     * @param from 原单位
     * @param to 目标单位
     * @return 换算后的风切变值
     */
    public static double convertWindShear(double value, WindShearUnit from, WindShearUnit to) {
        if (from == to) {
            return value;
        }
        return switch (from) {
            case METERS_PER_SECOND_PER_KILOMETER -> switch (to) {
                case MILES_PER_HOUR_PER_KILOFOOT -> value * MPH_PER_KILOFOOT_PER_MPS_PER_KM;
                default -> throw unsupported(from, to);
            };
            case MILES_PER_HOUR_PER_KILOFOOT -> switch (to) {
                case METERS_PER_SECOND_PER_KILOMETER -> value / MPH_PER_KILOFOOT_PER_MPS_PER_KM;
                default -> throw unsupported(from, to);
            };
        };
    }

    /**
     * 换算剂量单位。
     *
     * @param value 原始剂量值
     * @param from 原单位
     * @param to 目标单位
     * @return 换算后的剂量值
     */
    public static double convertDose(double value, DoseUnit from, DoseUnit to) {
        if (from == to) {
            return value;
        }
        return switch (from) {
            case ROENTGEN -> switch (to) {
                case SIEVERT -> value / 100.0d;
                default -> throw unsupported(from, to);
            };
            case SIEVERT -> switch (to) {
                case ROENTGEN -> value * 100.0d;
                default -> throw unsupported(from, to);
            };
        };
    }

    /**
     * 构造统一的“不支持该单位组合”异常。
     *
     * @param from 原单位
     * @param to 目标单位
     * @return 异常对象
     */
    private static UnknownUnitException unsupported(Enum<?> from, Enum<?> to) {
        return new UnknownUnitException("Unsupported conversion: " + from + " -> " + to);
    }
}
