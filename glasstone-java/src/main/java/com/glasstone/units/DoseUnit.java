package com.glasstone.units;

/**
 * 辐射剂量单位枚举。
 */
public enum DoseUnit {
    /**
     * 伦琴。
     */
    ROENTGEN("Roentgen"),
    /**
     * 希沃特。
     */
    SIEVERT("Sv");

    /**
     * 与该枚举值对应的文本缩写。
     */
    private final String symbol;

    /**
     * 使用单位缩写创建枚举值。
     *
     * @param symbol 单位的显示文本
     */
    DoseUnit(String symbol) {
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
