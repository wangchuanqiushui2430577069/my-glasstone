package com.glasstone.units;

/**
 * 压力单位枚举。
 */
public enum PressureUnit {
    /**
     * 磅力每平方英寸。
     */
    PSI("psi"),
    /**
     * 千克力每平方厘米。
     */
    KILOGRAM_PER_CM2("kg/cm^2"),
    /**
     * 兆帕。
     */
    MEGAPASCAL("MPa"),
    /**
     * 帕斯卡。
     */
    PASCAL("Pa");

    /**
     * 与该枚举值对应的文本缩写。
     */
    private final String symbol;

    /**
     * 使用单位缩写创建枚举值。
     *
     * @param symbol 单位的显示文本
     */
    PressureUnit(String symbol) {
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
