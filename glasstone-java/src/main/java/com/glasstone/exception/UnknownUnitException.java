package com.glasstone.exception;

/**
 * 表示调用方请求了当前库未支持的单位换算。
 * <p>
 * 该异常通常在单位枚举组合未被显式实现时抛出，用于提醒调用方检查入参。
 */
public class UnknownUnitException extends IllegalArgumentException {
    /**
     * 使用说明文本构造异常。
     *
     * @param message 异常说明
     */
    public UnknownUnitException(String message) {
        super(message);
    }
}
