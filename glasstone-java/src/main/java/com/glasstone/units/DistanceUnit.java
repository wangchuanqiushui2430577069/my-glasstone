package com.glasstone.units;

/**
 * 距离单位枚举。
 */
public enum DistanceUnit {
    /**
     * 米。
     */
    METERS("m"),
    /**
     * 千米。
     */
    KILOMETERS("km"),
    /**
     * 千英尺。
     */
    KILOFEET("kilofeet"),
    /**
     * 英里。
     */
    MILES("mi"),
    /**
     * 码。
     */
    YARDS("yards"),
    /**
     * 英尺。
     */
    FEET("ft");

    /**
     * 与该枚举值对应的文本缩写。
     */
    private final String symbol;

    /**
     * 使用单位缩写创建枚举值。
     *
     * @param symbol 单位的显示文本
     */
    DistanceUnit(String symbol) {
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
