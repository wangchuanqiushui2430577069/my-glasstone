package com.glasstone.units;

/**
 * 速度单位枚举。
 */
public enum SpeedUnit {
    /**
     * 米每秒。
     */
    METERS_PER_SECOND("m/s"),
    /**
     * 英里每小时。
     */
    MILES_PER_HOUR("mph"),
    /**
     * 千米每小时。
     */
    KILOMETERS_PER_HOUR("km/h");

    /**
     * 与该枚举值对应的文本缩写。
     */
    private final String symbol;

    /**
     * 使用单位缩写创建枚举值。
     *
     * @param symbol 单位的显示文本
     */
    SpeedUnit(String symbol) {
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
