package com.glasstone.examples.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import javax.swing.JPanel;
import org.hipparchus.util.FastMath;

/**
 * 简单的 3D 线框曲面绘图面板。
 * <p>
 * 该组件通过等轴测投影把二维网格映射到屏幕坐标。
 */
public final class SurfacePlotPanel extends JPanel {
    /**
     * 坐标轴与线框曲面所用描边样式。
     */
    private static final Stroke AXIS_STROKE = new BasicStroke(1.4f);
    private static final Stroke SERIES_STROKE = new BasicStroke(1.1f);
    /**
     * 面板默认尺寸。
     */
    private static final int PREFERRED_WIDTH = 1080;
    private static final int PREFERRED_HEIGHT = 760;
    /**
     * 默认视角与缩放配置。
     */
    private static final double DEFAULT_AZIMUTH_DEGREES = 45.0d;
    private static final double DEFAULT_ELEVATION_DEGREES = 30.0d;
    private static final double DEFAULT_ZOOM_FACTOR = 1.0d;
    private static final double ZOOM_STEP = 1.12d;
    private static final double MIN_ZOOM_FACTOR = 0.45d;
    private static final double MAX_ZOOM_FACTOR = 3.5d;
    /**
     * 鼠标拖拽与视角变化之间的灵敏度系数。
     */
    private static final double DRAG_AZIMUTH_DEGREES_PER_PIXEL = 0.55d;
    private static final double DRAG_ELEVATION_DEGREES_PER_PIXEL = 0.35d;

    /**
     * 图表标题、坐标轴文本与原始网格数据。
     */
    private final String title;
    private final String xLabel;
    private final String yLabel;
    private final String zLabel;
    private final double[] xs;
    private final double[] ys;
    private final List<SurfaceSeries> series;
    /**
     * 三个坐标轴的显示范围。
     */
    private final double xMin;
    private final double xMax;
    private final double yMin;
    private final double yMax;
    private final double zMin;
    private final double zMax;
    /**
     * 图例背景与边框颜色，可按示例主题覆盖。
     */
    private Color legendBackgroundColor = new Color(250, 250, 250);
    private Color legendBorderColor = Color.LIGHT_GRAY;
    /**
     * 当前视角与缩放状态。
     */
    private double azimuthDegrees = DEFAULT_AZIMUTH_DEGREES;
    private double elevationDegrees = DEFAULT_ELEVATION_DEGREES;
    private double zoomFactor = DEFAULT_ZOOM_FACTOR;
    /**
     * 上一次拖拽时的鼠标位置。
     */
    private Point lastDragPoint;

    /**
     * 创建一个 3D 线框绘图面板。
     */
    public SurfacePlotPanel(
            String title,
            String xLabel,
            String yLabel,
            String zLabel,
            double[] xs,
            double[] ys,
            List<SurfaceSeries> series,
            double zMin,
            double zMax
    ) {
        this(title, xLabel, yLabel, zLabel, xs, ys, series, xs[0], xs[xs.length - 1], ys[0], ys[ys.length - 1], zMin, zMax);
    }

    /**
     * 创建一个 3D 线框绘图面板，并允许单独指定坐标轴显示方向。
     */
    public SurfacePlotPanel(
            String title,
            String xLabel,
            String yLabel,
            String zLabel,
            double[] xs,
            double[] ys,
            List<SurfaceSeries> series,
            double xMin,
            double xMax,
            double yMin,
            double yMax,
            double zMin,
            double zMax
    ) {
        this.title = title;
        this.xLabel = xLabel;
        this.yLabel = yLabel;
        this.zLabel = zLabel;
        this.xs = xs.clone();
        this.ys = ys.clone();
        this.series = List.copyOf(series);
        this.xMin = xMin;
        this.xMax = xMax;
        this.yMin = yMin;
        this.yMax = yMax;
        this.zMin = zMin;
        this.zMax = zMax;
        setBackground(Color.WHITE);
        setForeground(Color.BLACK);
        setPreferredSize(new Dimension(PREFERRED_WIDTH, PREFERRED_HEIGHT));
        setToolTipText("拖动旋转视角，滚轮缩放，双击重置");
        addMouseWheelListener(this::handleMouseWheelZoom);
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                lastDragPoint = event.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (lastDragPoint == null) {
                    lastDragPoint = event.getPoint();
                    return;
                }
                int deltaX = event.getX() - lastDragPoint.x;
                int deltaY = event.getY() - lastDragPoint.y;
                rotateView(deltaX, deltaY);
                lastDragPoint = event.getPoint();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                lastDragPoint = null;
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    resetView();
                }
            }
        };
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
    }

    /**
     * 覆盖面板与图例的主题颜色。
     */
    public void setThemeColors(Color backgroundColor, Color foregroundColor, Color legendBackgroundColor, Color legendBorderColor) {
        if (backgroundColor == null || foregroundColor == null || legendBackgroundColor == null || legendBorderColor == null) {
            throw new IllegalArgumentException("theme colors must not be null");
        }
        setBackground(backgroundColor);
        setForeground(foregroundColor);
        this.legendBackgroundColor = legendBackgroundColor;
        this.legendBorderColor = legendBorderColor;
        repaint();
    }

    /**
     * 设置当前视角。
     *
     * @param azimuthDegrees 方位角，单位度
     * @param elevationDegrees 仰角，单位度
     */
    public void setViewAngles(double azimuthDegrees, double elevationDegrees) {
        this.azimuthDegrees = normalizeAzimuth(azimuthDegrees);
        this.elevationDegrees = clamp(elevationDegrees, 5.0d, 85.0d);
        repaint();
    }

    /**
     * 返回当前方位角。
     *
     * @return 方位角，单位度
     */
    public double azimuthDegrees() {
        return azimuthDegrees;
    }

    /**
     * 返回当前仰角。
     *
     * @return 仰角，单位度
     */
    public double elevationDegrees() {
        return elevationDegrees;
    }

    /**
     * 返回当前缩放倍率。
     *
     * @return 缩放倍率
     */
    public double zoomFactor() {
        return zoomFactor;
    }

    /**
     * 重置为默认视角与缩放。
     */
    public void resetView() {
        azimuthDegrees = DEFAULT_AZIMUTH_DEGREES;
        elevationDegrees = DEFAULT_ELEVATION_DEGREES;
        zoomFactor = DEFAULT_ZOOM_FACTOR;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // plotArea: 主绘图区域；右侧保留图例区域。
        Rectangle2D plotArea = new Rectangle2D.Double(80.0d, 50.0d, getWidth() - 280.0d, getHeight() - 120.0d);
        drawTitle(g2);
        drawAxes(g2, plotArea);
        drawSeries(g2, plotArea);
        drawLegend(g2, new Rectangle2D.Double(getWidth() - 180.0d, 110.0d, 150.0d, 180.0d));

        g2.dispose();
    }

    /**
     * 绘制标题。
     */
    private void drawTitle(Graphics2D g2) {
        FontMetrics metrics = g2.getFontMetrics(g2.getFont().deriveFont(18.0f));
        g2.setFont(g2.getFont().deriveFont(18.0f));
        int titleWidth = metrics.stringWidth(title);
        g2.setColor(getForeground());
        g2.drawString(title, (getWidth() - titleWidth) / 2, 28);
        g2.setFont(g2.getFont().deriveFont(12.0f));
    }

    /**
     * 绘制三根坐标轴及刻度。
     */
    private void drawAxes(Graphics2D g2, Rectangle2D plotArea) {
        g2.setColor(getForeground());
        g2.setStroke(AXIS_STROKE);

        Point2D origin = project(xMin, yMin, zMin, plotArea);
        Point2D xAxisEnd = project(xMax, yMin, zMin, plotArea);
        Point2D yAxisEnd = project(xMin, yMax, zMin, plotArea);
        Point2D zAxisEnd = project(xMin, yMin, zMax, plotArea);

        g2.draw(new Line2D.Double(origin, xAxisEnd));
        g2.draw(new Line2D.Double(origin, yAxisEnd));
        g2.draw(new Line2D.Double(origin, zAxisEnd));

        drawAxisTicks(g2, plotArea, true, false, false, xLabel, xMin, xMax);
        drawAxisTicks(g2, plotArea, false, true, false, yLabel, yMin, yMax);
        drawAxisTicks(g2, plotArea, false, false, true, zLabel, zMin, zMax);
    }

    /**
     * 绘制单根坐标轴的刻度和标签。
     */
    private void drawAxisTicks(
            Graphics2D g2,
            Rectangle2D plotArea,
            boolean xAxis,
            boolean yAxis,
            boolean zAxis,
            String label,
            double min,
            double max
    ) {
        for (int tickIndex = 0; tickIndex <= 4; tickIndex++) {
            double ratio = tickIndex / 4.0d;
            double value = min + ((max - min) * ratio);
            Point2D point = xAxis
                    ? project(value, yMin, zMin, plotArea)
                    : yAxis
                    ? project(xMin, value, zMin, plotArea)
                    : project(xMin, yMin, value, plotArea);
            g2.fillOval((int) point.getX() - 2, (int) point.getY() - 2, 4, 4);
            g2.drawString(formatTick(value), (float) point.getX() + 6.0f, (float) point.getY() + 4.0f);
        }

        Point2D labelPoint = xAxis
                ? project(xMax, yMin, zMin, plotArea)
                : yAxis
                ? project(xMin, yMax, zMin, plotArea)
                : project(xMin, yMin, zMax, plotArea);
        g2.drawString(label, (float) labelPoint.getX() + 8.0f, (float) labelPoint.getY() - 8.0f);
    }

    /**
     * 绘制所有曲面序列。
     */
    private void drawSeries(Graphics2D g2, Rectangle2D plotArea) {
        for (SurfaceSeries item : series) {
            g2.setColor(item.color());
            g2.setStroke(SERIES_STROKE);
            for (int row = 0; row < ys.length; row += item.rowStride()) {
                drawRow(g2, plotArea, item.values(), row);
            }
            for (int column = 0; column < xs.length; column += item.columnStride()) {
                drawColumn(g2, plotArea, item.values(), column);
            }
        }
    }

    /**
     * 沿着某一行绘制折线。
     */
    private void drawRow(Graphics2D g2, Rectangle2D plotArea, double[][] values, int row) {
        Point2D previous = null;
        for (int column = 0; column < xs.length; column++) {
            Point2D current = project(xs[column], ys[row], values[row][column], plotArea);
            if (previous != null) {
                g2.draw(new Line2D.Double(previous, current));
            }
            previous = current;
        }
    }

    /**
     * 沿着某一列绘制折线。
     */
    private void drawColumn(Graphics2D g2, Rectangle2D plotArea, double[][] values, int column) {
        Point2D previous = null;
        for (int row = 0; row < ys.length; row++) {
            Point2D current = project(xs[column], ys[row], values[row][column], plotArea);
            if (previous != null) {
                g2.draw(new Line2D.Double(previous, current));
            }
            previous = current;
        }
    }

    /**
     * 绘制图例。
     */
    private void drawLegend(Graphics2D g2, Rectangle2D area) {
        g2.setColor(legendBackgroundColor);
        g2.fill(area);
        g2.setColor(legendBorderColor);
        g2.draw(area);
        g2.setColor(getForeground());
        g2.drawString("Legend", (float) area.getX() + 10.0f, (float) area.getY() + 20.0f);

        float y = (float) area.getY() + 45.0f;
        for (SurfaceSeries item : series) {
            g2.setColor(item.color());
            g2.drawLine((int) area.getX() + 10, (int) y, (int) area.getX() + 36, (int) y);
            g2.setColor(getForeground());
            g2.drawString(item.name(), (float) area.getX() + 46.0f, y + 4.0f);
            y += 24.0f;
        }
    }

    /**
     * 把三维点投影到二维屏幕坐标。
     */
    private Point2D project(double x, double y, double z, Rectangle2D plotArea) {
        // nx/ny/nz: 归一化并中心化后的三维坐标。
        double nx = normalize(x, xMin, xMax) - 0.5d;
        double ny = normalize(y, yMin, yMax) - 0.5d;
        double nz = normalize(z, zMin, zMax) - 0.5d;

        // azimuth/elevation: 当前观察方位角和仰角。
        double azimuth = FastMath.toRadians(azimuthDegrees);
        double elevation = FastMath.toRadians(elevationDegrees);

        // rotatedX/rotatedY: 先绕垂直轴旋转后的水平坐标。
        double rotatedX = (nx * FastMath.cos(azimuth)) - (ny * FastMath.sin(azimuth));
        double rotatedY = (nx * FastMath.sin(azimuth)) + (ny * FastMath.cos(azimuth));

        // projectedZ: 再按仰角把深度与高度合成到屏幕纵向。
        double projectedZ = (rotatedY * FastMath.sin(elevation)) + (nz * FastMath.cos(elevation));

        double scale = FastMath.min(plotArea.getWidth(), plotArea.getHeight()) * 0.82d * zoomFactor;
        double baseX = plotArea.getCenterX();
        double baseY = plotArea.getCenterY() + (plotArea.getHeight() * 0.18d);

        double px = baseX + (scale * 1.15d * rotatedX);
        double py = baseY - (scale * 1.35d * projectedZ);
        return new Point2D.Double(px, py);
    }

    /**
     * 把数值归一化到 [0, 1] 区间。
     */
    private double normalize(double value, double minimum, double maximum) {
        if (maximum == minimum) {
            return 0.0d;
        }
        return (value - minimum) / (maximum - minimum);
    }

    /**
     * 格式化刻度文本。
     */
    private String formatTick(double value) {
        if (FastMath.abs(value) >= 1000.0d) {
            return String.format("%.0f", value);
        }
        if (FastMath.abs(value - FastMath.rint(value)) < 1.0e-9d) {
            return String.format("%.0f", value);
        }
        return String.format("%.1f", value);
    }

    /**
     * 将方位角规范化到 [0, 360) 区间。
     */
    private double normalizeAzimuth(double value) {
        double normalized = value % 360.0d;
        return normalized < 0.0d ? normalized + 360.0d : normalized;
    }

    /**
     * 对视角输入做限幅。
     */
    private double clamp(double value, double minimum, double maximum) {
        return FastMath.max(minimum, FastMath.min(maximum, value));
    }

    void rotateView(int deltaX, int deltaY) {
        setViewAngles(
                azimuthDegrees + (deltaX * DRAG_AZIMUTH_DEGREES_PER_PIXEL),
                elevationDegrees - (deltaY * DRAG_ELEVATION_DEGREES_PER_PIXEL)
        );
    }

    /**
     * 处理鼠标滚轮缩放。
     */
    private void handleMouseWheelZoom(MouseWheelEvent event) {
        if (event.getPreciseWheelRotation() < 0.0d) {
            zoomFactor = clamp(zoomFactor * ZOOM_STEP, MIN_ZOOM_FACTOR, MAX_ZOOM_FACTOR);
        } else {
            zoomFactor = clamp(zoomFactor / ZOOM_STEP, MIN_ZOOM_FACTOR, MAX_ZOOM_FACTOR);
        }
        repaint();
    }
}
