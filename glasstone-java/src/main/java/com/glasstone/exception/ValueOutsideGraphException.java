package com.glasstone.exception;

/**
 * 表示输入值落在原始经验图表或拟合曲线的有效范围之外。
 * <p>
 * 该异常会在原始图表数据无法支持当前插值请求时抛出，以便调用方决定
 * 是回退到边界值、裁剪显示范围，还是直接把异常继续上抛。
 */
public class ValueOutsideGraphException extends RuntimeException {
    /**
     * 触发异常的原始输入值。
     */
    private final Object value;

    /**
     * 使用越界值构造异常。
     *
     * @param value 超出图表范围的输入
     */
    public ValueOutsideGraphException(Object value) {
        super("Value falls outside source graph range: " + value);
        this.value = value;
    }

    /**
     * 返回触发异常的原始输入。
     *
     * @return 越界值
     */
    public Object getValue() {
        return value;
    }
}
