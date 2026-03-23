package com.glasstone.units;

/**
 * 风切变单位枚举。
 */
public enum WindShearUnit {
    /**
     * 米每秒每千米。
     */
    METERS_PER_SECOND_PER_KILOMETER("m/s-km"),
    /**
     * 英里每小时每千英尺。
     */
    MILES_PER_HOUR_PER_KILOFOOT("mph/kilofoot");

    /**
     * 与该枚举值对应的文本缩写。
     */
    private final String symbol;

    /**
     * 使用单位缩写创建枚举值。
     *
     * @param symbol 单位的显示文本
     */
    WindShearUnit(String symbol) {
        this.symbol = symbol;
    }

    /**
     * 返回单位缩写。
     *
     * @return 单位符号
     */
    public String symbol() {
        return symbol;
    }
}
