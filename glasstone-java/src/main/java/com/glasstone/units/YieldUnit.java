package com.glasstone.units;

/**
 * 核爆当量单位枚举。
 */
public enum YieldUnit {
    /**
     * 千吨 TNT 当量。
     */
    KILOTONS("kT"),
    /**
     * 百万吨 TNT 当量。
     */
    MEGATONS("MT");

    /**
     * 与该枚举值对应的文本缩写。
     */
    private final String symbol;

    /**
     * 使用单位缩写创建枚举值。
     *
     * @param symbol 单位的显示文本
     */
    YieldUnit(String symbol) {
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
