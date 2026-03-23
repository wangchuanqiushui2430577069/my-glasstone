package com.glasstone.examples.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import javax.swing.JPanel;
import org.hipparchus.util.FastMath;

/**
 * 二维等值线与色带图面板。
 */
public final class ContourPlotPanel extends JPanel {
    /**
     * 默认首选宽度。
     */
    private static final int PREFERRED_WIDTH = 1040;
    /**
     * 默认首选高度。
     */
    private static final int PREFERRED_HEIGHT = 760;

    /**
     * 图表标题与轴标签等显示文本。
     */
    private final String title;
    private final String xLabel;
    private final String yLabel;
    private final String colorBarLabel;
    /**
     * 原始网格坐标和值数据。
     */
    private final double[] xs;
    private final double[] ys;
    private final double[][] values;
    private final double[] levels;
    /**
     * 色带与标注框相关的配色配置。
     */
    private final Color[] bandColors;
    private final Color underColor;
    private final Color overColor;
    private final String annotation;
    private final boolean annotationBoxVisible;
    private final double annotationX;
    private final double annotationY;
    /**
     * 是否绘制填色带和背景网格。
     */
    private final boolean fillBands;
    private final boolean showGrid;
    /**
     * 当前显示坐标范围。
     */
    private final double xDisplayMin;
    private final double xDisplayMax;
    private final double yDisplayMin;
    private final double yDisplayMax;

    /**
     * 创建一个二维等值线图面板。
     */
    public ContourPlotPanel(
            String title,
            String xLabel,
            String yLabel,
            String colorBarLabel,
            double[] xs,
            double[] ys,
            double[][] values,
            double[] levels,
            Color[] bandColors,
            Color underColor,
            Color overColor,
            String annotation
    ) {
        this(title, xLabel, yLabel, colorBarLabel, xs, ys, values, levels, bandColors, underColor, overColor, annotation,
                true, false, xs[0], xs[xs.length - 1], ys[0], ys[ys.length - 1]);
    }

    /**
     * 创建一个二维等值线图面板，并允许控制填色与网格显示。
     */
    public ContourPlotPanel(
            String title,
            String xLabel,
            String yLabel,
            String colorBarLabel,
            double[] xs,
            double[] ys,
            double[][] values,
            double[] levels,
            Color[] bandColors,
            Color underColor,
            Color overColor,
            String annotation,
            boolean fillBands,
            boolean showGrid
    ) {
        this(title, xLabel, yLabel, colorBarLabel, xs, ys, values, levels, bandColors, underColor, overColor, annotation,
                fillBands, showGrid, xs[0], xs[xs.length - 1], ys[0], ys[ys.length - 1]);
    }

    /**
     * 创建一个二维等值线图面板，并允许控制填色、网格和显示坐标范围。
     */
    public ContourPlotPanel(
            String title,
            String xLabel,
            String yLabel,
            String colorBarLabel,
            double[] xs,
            double[] ys,
            double[][] values,
            double[] levels,
            Color[] bandColors,
            Color underColor,
            Color overColor,
            String annotation,
            boolean fillBands,
            boolean showGrid,
            double xDisplayMin,
            double xDisplayMax,
            double yDisplayMin,
            double yDisplayMax
    ) {
        this(title, xLabel, yLabel, colorBarLabel, xs, ys, values, levels, bandColors, underColor, overColor, annotation,
                fillBands, showGrid, xDisplayMin, xDisplayMax, yDisplayMin, yDisplayMax, true, Double.NaN, Double.NaN);
    }

    /**
     * 创建一个二维等值线图面板，并允许控制注释的样式与绘制位置。
     */
    public ContourPlotPanel(
            String title,
            String xLabel,
            String yLabel,
            String colorBarLabel,
            double[] xs,
            double[] ys,
            double[][] values,
            double[] levels,
            Color[] bandColors,
            Color underColor,
            Color overColor,
            String annotation,
            boolean fillBands,
            boolean showGrid,
            double xDisplayMin,
            double xDisplayMax,
            double yDisplayMin,
            double yDisplayMax,
            boolean annotationBoxVisible,
            double annotationX,
            double annotationY
    ) {
        this.title = title;
        this.xLabel = xLabel;
        this.yLabel = yLabel;
        this.colorBarLabel = colorBarLabel;
        this.xs = xs.clone();
        this.ys = ys.clone();
        this.values = values;
        this.levels = levels.clone();
        this.bandColors = bandColors.clone();
        this.underColor = underColor;
        this.overColor = overColor;
        this.annotation = annotation;
        this.annotationBoxVisible = annotationBoxVisible;
        this.annotationX = annotationX;
        this.annotationY = annotationY;
        this.fillBands = fillBands;
        this.showGrid = showGrid;
        this.xDisplayMin = xDisplayMin;
        this.xDisplayMax = xDisplayMax;
        this.yDisplayMin = yDisplayMin;
        this.yDisplayMax = yDisplayMax;
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(PREFERRED_WIDTH, PREFERRED_HEIGHT));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // plotArea: 主绘图区；colorBarArea: 右侧色带区域。
        Rectangle2D plotArea = new Rectangle2D.Double(85.0d, 60.0d, getWidth() - 230.0d, getHeight() - 120.0d);
        Rectangle2D colorBarArea = new Rectangle2D.Double(getWidth() - 110.0d, 90.0d, 28.0d, getHeight() - 180.0d);

        drawTitle(g2);
        Shape originalClip = g2.getClip();
        g2.clip(plotArea);
        if (fillBands) {
            drawHeatmap(g2, plotArea);
        }
        if (showGrid) {
            drawGrid(g2, plotArea);
        }
        drawContours(g2, plotArea);
        g2.setClip(originalClip);
        drawAxes(g2, plotArea);
        drawAnnotation(g2, plotArea);
        drawColorBar(g2, colorBarArea);

        g2.dispose();
    }

    /**
     * 绘制标题。
     */
    private void drawTitle(Graphics2D g2) {
        FontMetrics metrics = g2.getFontMetrics(g2.getFont().deriveFont(18.0f));
        g2.setFont(g2.getFont().deriveFont(18.0f));
        int titleWidth = metrics.stringWidth(title);
        g2.setColor(Color.BLACK);
        g2.drawString(title, (getWidth() - titleWidth) / 2, 30);
        g2.setFont(g2.getFont().deriveFont(12.0f));
    }

    /**
     * 绘制热力底图。
     */
    private void drawHeatmap(Graphics2D g2, Rectangle2D plotArea) {
        for (int yIndex = 0; yIndex < ys.length - 1; yIndex++) {
            for (int xIndex = 0; xIndex < xs.length - 1; xIndex++) {
                // average: 单元格四角均值，用于决定填充色。
                double average = (values[yIndex][xIndex]
                        + values[yIndex][xIndex + 1]
                        + values[yIndex + 1][xIndex]
                        + values[yIndex + 1][xIndex + 1]) / 4.0d;
                if (!Double.isFinite(average)) {
                    continue;
                }
                g2.setColor(colorForValue(average));
                Rectangle2D cell = cellBounds(plotArea, xs[xIndex], xs[xIndex + 1], ys[yIndex], ys[yIndex + 1]);
                g2.fill(cell);
            }
        }
    }

    /**
     * 绘制背景网格线。
     */
    private void drawGrid(Graphics2D g2, Rectangle2D plotArea) {
        g2.setColor(new Color(215, 215, 215));
        g2.setStroke(new BasicStroke(1.0f));
        for (double xTick : visibleTickValues(xDisplayMin, xDisplayMax)) {
            double tickX = toPoint(plotArea, xTick, yDisplayMin).getX();
            g2.draw(new Line2D.Double(tickX, plotArea.getY(), tickX, plotArea.getMaxY()));
        }
        for (double yTick : visibleTickValues(yDisplayMin, yDisplayMax)) {
            double tickY = toPoint(plotArea, xDisplayMin, yTick).getY();
            g2.draw(new Line2D.Double(plotArea.getX(), tickY, plotArea.getMaxX(), tickY));
        }
    }

    /**
     * 绘制所有等值线。
     */
    private void drawContours(Graphics2D g2, Rectangle2D plotArea) {
        g2.setStroke(new BasicStroke(1.2f));
        for (int levelIndex = 0; levelIndex < levels.length; levelIndex++) {
            double level = levels[levelIndex];
            g2.setColor(contourColor(levelIndex));
            for (int yIndex = 0; yIndex < ys.length - 1; yIndex++) {
                for (int xIndex = 0; xIndex < xs.length - 1; xIndex++) {
                    drawContourCell(g2, plotArea, xIndex, yIndex, level);
                }
            }
        }
    }

    /**
     * 在单个网格单元上执行 marching squares。
     */
    private void drawContourCell(Graphics2D g2, Rectangle2D plotArea, int xIndex, int yIndex, double level) {
        double x0 = xs[xIndex];
        double x1 = xs[xIndex + 1];
        double y0 = ys[yIndex];
        double y1 = ys[yIndex + 1];

        double bottomLeft = values[yIndex][xIndex];
        double bottomRight = values[yIndex][xIndex + 1];
        double topLeft = values[yIndex + 1][xIndex];
        double topRight = values[yIndex + 1][xIndex + 1];
        if (!Double.isFinite(bottomLeft)
                || !Double.isFinite(bottomRight)
                || !Double.isFinite(topLeft)
                || !Double.isFinite(topRight)) {
            return;
        }

        // code: marching squares 的四位状态码。
        int code = 0;
        if (bottomLeft >= level) {
            code |= 1;
        }
        if (bottomRight >= level) {
            code |= 2;
        }
        if (topRight >= level) {
            code |= 4;
        }
        if (topLeft >= level) {
            code |= 8;
        }

        if (code == 0 || code == 15) {
            return;
        }

        Point2D bottom = interpolatePoint(plotArea, x0, y0, bottomLeft, x1, y0, bottomRight, level);
        Point2D right = interpolatePoint(plotArea, x1, y0, bottomRight, x1, y1, topRight, level);
        Point2D top = interpolatePoint(plotArea, x0, y1, topLeft, x1, y1, topRight, level);
        Point2D left = interpolatePoint(plotArea, x0, y0, bottomLeft, x0, y1, topLeft, level);

        switch (code) {
            case 1, 14 -> drawSegment(g2, left, bottom);
            case 2, 13 -> drawSegment(g2, bottom, right);
            case 3, 12 -> drawSegment(g2, left, right);
            case 4, 11 -> drawSegment(g2, right, top);
            case 5 -> {
                drawSegment(g2, left, bottom);
                drawSegment(g2, right, top);
            }
            case 6, 9 -> drawSegment(g2, bottom, top);
            case 7, 8 -> drawSegment(g2, left, top);
            case 10 -> {
                drawSegment(g2, bottom, right);
                drawSegment(g2, left, top);
            }
            default -> {
            }
        }
    }

    /**
     * 绘制一条等值线段。
     */
    private void drawSegment(Graphics2D g2, Point2D from, Point2D to) {
        g2.draw(new Line2D.Double(from, to));
    }

    /**
     * 在线段端点之间按目标等值线做线性插值。
     */
    private Point2D interpolatePoint(
            Rectangle2D plotArea,
            double x0,
            double y0,
            double v0,
            double x1,
            double y1,
            double v1,
            double level
    ) {
        double ratio = v1 == v0 ? 0.5d : (level - v0) / (v1 - v0);
        ratio = FastMath.max(0.0d, FastMath.min(1.0d, ratio));
        double x = x0 + ((x1 - x0) * ratio);
        double y = y0 + ((y1 - y0) * ratio);
        return toPoint(plotArea, x, y);
    }

    /**
     * 绘制坐标轴和刻度。
     */
    private void drawAxes(Graphics2D g2, Rectangle2D plotArea) {
        g2.setColor(Color.DARK_GRAY);
        g2.draw(plotArea);

        FontMetrics metrics = g2.getFontMetrics();
        for (double xValue : visibleTickValues(xDisplayMin, xDisplayMax)) {
            double tickX = toPoint(plotArea, xValue, yDisplayMin).getX();
            g2.draw(new Line2D.Double(tickX, plotArea.getMaxY(), tickX, plotArea.getMaxY() + 5.0d));
            String label = formatTick(xValue);
            float labelX = (float) (tickX - (metrics.stringWidth(label) / 2.0d));
            g2.drawString(label, labelX, (float) plotArea.getMaxY() + 20.0f);
        }
        for (double yValue : visibleTickValues(yDisplayMin, yDisplayMax)) {
            double tickY = toPoint(plotArea, xDisplayMin, yValue).getY();
            g2.draw(new Line2D.Double(plotArea.getX() - 5.0d, tickY, plotArea.getX(), tickY));
            String label = formatTick(yValue);
            float labelX = (float) (plotArea.getX() - 10.0d - metrics.stringWidth(label));
            g2.drawString(label, labelX, (float) tickY + 4.0f);
        }

        g2.drawString(xLabel, (float) plotArea.getCenterX() - 35.0f, (float) plotArea.getMaxY() + 45.0f);
        g2.rotate(-FastMath.PI / 2.0d);
        g2.drawString(yLabel, (float) -plotArea.getCenterY() - 35.0f, (float) plotArea.getX() - 55.0f);
        g2.rotate(FastMath.PI / 2.0d);
    }

    /**
     * 绘制文字说明框。
     */
    private void drawAnnotation(Graphics2D g2, Rectangle2D plotArea) {
        if (annotation == null || annotation.isBlank()) {
            return;
        }
        String[] lines = annotation.split("\\R");
        FontMetrics metrics = g2.getFontMetrics();
        if (!annotationBoxVisible) {
            Point2D anchor = Double.isFinite(annotationX) && Double.isFinite(annotationY)
                    ? toPoint(plotArea, annotationX, annotationY)
                    : new Point2D.Double(plotArea.getX() + 16.0d, plotArea.getY() + 16.0d + metrics.getAscent());
            g2.setColor(Color.BLACK);
            float y = (float) anchor.getY();
            for (String line : lines) {
                g2.drawString(line, (float) anchor.getX(), y);
                y += metrics.getHeight();
            }
            return;
        }
        int width = 0;
        for (String line : lines) {
            width = FastMath.max(width, metrics.stringWidth(line));
        }
        double boxX = plotArea.getX() + 16.0d;
        double boxY = plotArea.getY() + 16.0d;
        double boxWidth = width + 18.0d;
        double boxHeight = (lines.length * metrics.getHeight()) + 14.0d;

        g2.setColor(new Color(255, 255, 255, 210));
        g2.fill(new Rectangle2D.Double(boxX, boxY, boxWidth, boxHeight));
        g2.setColor(Color.GRAY);
        g2.draw(new Rectangle2D.Double(boxX, boxY, boxWidth, boxHeight));
        g2.setColor(Color.BLACK);

        float y = (float) boxY + metrics.getAscent() + 6.0f;
        for (String line : lines) {
            g2.drawString(line, (float) boxX + 9.0f, y);
            y += metrics.getHeight();
        }
    }

    /**
     * 绘制右侧色带图。
     */
    private void drawColorBar(Graphics2D g2, Rectangle2D area) {
        double segmentHeight = area.getHeight() / (bandColors.length + 2.0d);
        double y = area.getY();

        g2.setColor(overColor);
        g2.fill(new Rectangle2D.Double(area.getX(), y, area.getWidth(), segmentHeight));
        y += segmentHeight;

        for (int index = bandColors.length - 1; index >= 0; index--) {
            g2.setColor(bandColors[index]);
            g2.fill(new Rectangle2D.Double(area.getX(), y, area.getWidth(), segmentHeight));
            y += segmentHeight;
        }

        g2.setColor(underColor);
        g2.fill(new Rectangle2D.Double(area.getX(), y, area.getWidth(), segmentHeight));
        g2.setColor(Color.DARK_GRAY);
        g2.draw(area);

        g2.drawString(colorBarLabel, (float) area.getX() - 10.0f, (float) area.getY() - 14.0f);
        g2.drawString(">" + formatLevel(levels[levels.length - 1]), (float) area.getMaxX() + 10.0f, (float) area.getY() + 12.0f);
        for (int index = levels.length - 2; index >= 0; index--) {
            float labelY = (float) (area.getY() + ((levels.length - 1 - index) * segmentHeight) + 12.0d);
            g2.drawString(formatLevel(levels[index]), (float) area.getMaxX() + 10.0f, labelY);
        }
        g2.drawString("<" + formatLevel(levels[0]), (float) area.getMaxX() + 10.0f, (float) area.getMaxY());
    }

    /**
     * 计算网格单元对应的屏幕矩形。
     */
    private Rectangle2D cellBounds(Rectangle2D plotArea, double x0, double x1, double y0, double y1) {
        Point2D bottomLeft = toPoint(plotArea, x0, y0);
        Point2D topRight = toPoint(plotArea, x1, y1);
        double left = FastMath.min(bottomLeft.getX(), topRight.getX());
        double top = FastMath.min(bottomLeft.getY(), topRight.getY());
        double width = FastMath.abs(topRight.getX() - bottomLeft.getX());
        double height = FastMath.abs(topRight.getY() - bottomLeft.getY());
        return new Rectangle2D.Double(left, top, width + 1.0d, height + 1.0d);
    }

    /**
     * 把数据坐标转换为屏幕坐标。
     */
    private Point2D toPoint(Rectangle2D plotArea, double x, double y) {
        double xRatio = (x - xDisplayMin) / (xDisplayMax - xDisplayMin);
        double yRatio = (y - yDisplayMin) / (yDisplayMax - yDisplayMin);
        double pixelX = plotArea.getX() + (plotArea.getWidth() * xRatio);
        double pixelY = plotArea.getMaxY() - (plotArea.getHeight() * yRatio);
        return new Point2D.Double(pixelX, pixelY);
    }

    static double[] visibleTickValues(double axisMinimum, double axisMaximum) {
        if (!(axisMaximum > axisMinimum)) {
            return new double[0];
        }
        double step = niceTickStep((axisMaximum - axisMinimum) / 5.0d);
        double firstTick = FastMath.ceil(axisMinimum / step) * step;
        double lastTick = FastMath.floor(axisMaximum / step) * step;
        int tickCount = (int) FastMath.floor(((lastTick - firstTick) / step) + 1.0e-9d) + 1;
        if (tickCount <= 0) {
            return new double[0];
        }
        double[] ticks = new double[tickCount];
        for (int index = 0; index < tickCount; index++) {
            ticks[index] = normalizeZero(firstTick + (index * step));
        }
        return ticks;
    }

    private static double niceTickStep(double rawStep) {
        double exponent = FastMath.floor(FastMath.log10(rawStep));
        double power = FastMath.pow(10.0d, exponent);
        double fraction = rawStep / power;
        double niceFraction;
        if (fraction <= 1.5d) {
            niceFraction = 1.0d;
        } else if (fraction <= 3.0d) {
            niceFraction = 2.0d;
        } else if (fraction <= 7.0d) {
            niceFraction = 5.0d;
        } else {
            niceFraction = 10.0d;
        }
        return niceFraction * power;
    }

    private static double normalizeZero(double value) {
        if (FastMath.abs(value) < 1.0e-9d) {
            return 0.0d;
        }
        return value;
    }

    static int colorBandIndex(double value, double[] levels) {
        if (!Double.isFinite(value) || value < levels[0]) {
            return -1;
        }
        for (int index = 0; index < levels.length - 1; index++) {
            if (value < levels[index + 1]) {
                return index;
            }
        }
        return levels.length - 1;
    }

    /**
     * 根据值落在哪个区间来选择色带颜色。
     */
    private Color colorForValue(double value) {
        int bandIndex = colorBandIndex(value, levels);
        if (bandIndex < 0) {
            return underColor;
        }
        if (bandIndex >= bandColors.length) {
            return overColor;
        }
        return bandColors[bandIndex];
    }

    /**
     * 为某个等值线级别选择描边颜色。
     */
    private Color contourColor(int levelIndex) {
        if (levelIndex >= bandColors.length) {
            return overColor;
        }
        return bandColors[levelIndex];
    }

    /**
     * 格式化坐标刻度。
     */
    private String formatTick(double value) {
        if (FastMath.abs(value - FastMath.rint(value)) < 1.0e-9d) {
            return String.format("%.0f", value);
        }
        return String.format("%.1f", value);
    }

    /**
     * 格式化色带标签。
     */
    private String formatLevel(double value) {
        if (value >= 1000.0d) {
            return String.format("%.0f", value);
        }
        if (FastMath.abs(value - FastMath.rint(value)) < 1.0e-9d) {
            return String.format("%.0f", value);
        }
        return String.format("%.2f", value);
    }
}
