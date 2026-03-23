package com.glasstone.examples.ui;

import java.awt.Color;

/**
 * 定义一组用于 3D 线框绘制的曲面数据。
 *
 * @param name 图例名称
 * @param color 曲面线框颜色
 * @param values 网格采样值，按 `[行][列]` 存储
 * @param rowStride 沿行方向的抽样步长
 * @param columnStride 沿列方向的抽样步长
 */
public record SurfaceSeries(
        String name,
        Color color,
        double[][] values,
        int rowStride,
        int columnStride
) {
    /**
     * 校验曲面序列的名称、颜色、网格和步长设置。
     */
    public SurfaceSeries {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (color == null) {
            throw new IllegalArgumentException("color must not be null");
        }
        if (values == null || values.length == 0 || values[0].length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        if (rowStride <= 0 || columnStride <= 0) {
            throw new IllegalArgumentException("strides must be positive");
        }
    }
}
