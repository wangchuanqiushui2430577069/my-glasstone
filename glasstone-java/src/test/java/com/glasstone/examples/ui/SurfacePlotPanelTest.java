package com.glasstone.examples.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * `SurfacePlotPanel` 的回归测试。
 */
class SurfacePlotPanelTest {
    /**
     * 构造一份最小可用的曲面绘图面板，供交互测试复用。
     *
     * @return 测试用 3D 曲面面板
     */
    private static SurfacePlotPanel panel() {
        double[] xs = {0.0d, 1.0d};
        double[] ys = {0.0d, 1.0d};
        double[][] values = {
                {0.0d, 1.0d},
                {1.0d, 2.0d}
        };
        return new SurfacePlotPanel(
                "title",
                "x",
                "y",
                "z",
                xs,
                ys,
                List.of(new SurfaceSeries("series", Color.BLUE, values, 1, 1)),
                0.0d,
                2.0d
        );
    }

    /**
     * 验证鼠标拖拽对应的视角旋转量会被正确应用。
     */
    @Test
    void rotatesViewFromMouseDrag() {
        SurfacePlotPanel panel = panel();

        panel.rotateView(20, -10);

        assertEquals(56.0d, panel.azimuthDegrees(), 1.0e-9);
        assertEquals(33.5d, panel.elevationDegrees(), 1.0e-9);
    }

    /**
     * 验证重置操作会恢复默认视角和缩放倍率。
     */
    @Test
    void resetsViewToDefaults() {
        SurfacePlotPanel panel = panel();
        panel.setViewAngles(120.0d, 60.0d);
        panel.resetView();

        assertEquals(45.0d, panel.azimuthDegrees(), 1.0e-9);
        assertEquals(30.0d, panel.elevationDegrees(), 1.0e-9);
        assertEquals(1.0d, panel.zoomFactor(), 1.0e-9);
    }
}
