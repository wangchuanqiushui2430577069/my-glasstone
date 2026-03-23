package com.glasstone.examples.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/**
 * `ContourPlotPanel` 的回归测试。
 */
class ContourPlotPanelTest {
    /**
     * 验证非有限值会被归入下溢色带。
     */
    @Test
    void treatsNaNAsUnderflowBand() {
        double[] levels = {0.1d, 0.5d, 0.75d, 0.95d, 1.0d};

        assertEquals(-1, ContourPlotPanel.colorBandIndex(Double.NaN, levels));
    }

    /**
     * 验证普通区间值与溢出区间值的分段索引计算正确。
     */
    @Test
    void classifiesInteriorAndOverflowBands() {
        double[] levels = {0.1d, 0.5d, 0.75d, 0.95d, 1.0d};

        assertEquals(0, ContourPlotPanel.colorBandIndex(0.2d, levels));
        assertEquals(2, ContourPlotPanel.colorBandIndex(0.8d, levels));
        assertEquals(4, ContourPlotPanel.colorBandIndex(1.01d, levels));
    }

    /**
     * 验证可见刻度位置与 Python 示例的显示效果保持一致。
     */
    @Test
    void matchesPythonLikeVisibleTicks() {
        assertArrayEquals(new double[]{0.0d, 2.0d, 4.0d, 6.0d, 8.0d},
                ContourPlotPanel.visibleTickValues(-1.0d, 9.95d));
        assertArrayEquals(new double[]{0.0d, 2.0d, 4.0d, 6.0d, 8.0d, 10.0d},
                ContourPlotPanel.visibleTickValues(-0.5d, 11.0d));
    }
}
