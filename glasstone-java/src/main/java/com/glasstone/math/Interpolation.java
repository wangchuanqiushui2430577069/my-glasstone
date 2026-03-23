package com.glasstone.math;

import com.glasstone.exception.ValueOutsideGraphException;
import org.hipparchus.analysis.UnivariateFunction;
import org.hipparchus.analysis.interpolation.LinearInterpolator;
import org.hipparchus.util.MathArrays;

/**
 * 统一封装一维线性插值逻辑。
 */
public final class Interpolation {
    /**
     * Hipparchus 线性插值器实例。
     */
    private static final LinearInterpolator LINEAR_INTERPOLATOR = new LinearInterpolator();

    /**
     * 私有构造器，禁止实例化工具类。
     */
    private Interpolation() {
    }

    /**
     * 在两个点之间做一次显式线性插值。
     *
     * @param x 待求横坐标
     * @param x0 左端点横坐标
     * @param x1 右端点横坐标
     * @param y0 左端点纵坐标
     * @param y1 右端点纵坐标
     * @return 插值结果
     */
    public static double linear(double x, double x0, double x1, double y0, double y1) {
        if (Double.compare(x0, x1) == 0) {
            throw new IllegalArgumentException("Interpolation points must have distinct x values.");
        }
        // 左右权重之和为 1，用于稳定地组合两个端点值。
        double leftWeight = (x1 - x) / (x1 - x0);
        double rightWeight = (x - x0) / (x1 - x0);
        return MathArrays.linearCombination(leftWeight, y0, rightWeight, y1);
    }

    /**
     * 对一组离散点执行一维线性插值。
     *
     * @param x 待求横坐标
     * @param xs 横坐标数组
     * @param ys 纵坐标数组
     * @return 插值结果
     */
    public static double interpolate(double x, double[] xs, double[] ys) {
        MathArrays.checkEqualLength(xs, ys);
        if (xs.length < 2) {
            throw new IllegalArgumentException("At least two interpolation points are required.");
        }

        // 先复制再排序，避免调用方原始数组被修改。
        double[] sortedXs = xs.clone();
        double[] sortedYs = ys.clone();
        MathArrays.sortInPlace(sortedXs, sortedYs);
        ensureDistinct(sortedXs);

        if (outOfRange(x, sortedXs[0], sortedXs[sortedXs.length - 1])) {
            throw new ValueOutsideGraphException(x);
        }

        UnivariateFunction function = LINEAR_INTERPOLATOR.interpolate(sortedXs, sortedYs);
        return function.value(x);
    }

    /**
     * 检查横坐标数组中是否存在重复点。
     *
     * @param xs 已排序的横坐标数组
     */
    private static void ensureDistinct(double[] xs) {
        for (int index = 1; index < xs.length; index++) {
            if (Double.compare(xs[index], xs[index - 1]) == 0) {
                throw new IllegalArgumentException("Interpolation points must have distinct x values.");
            }
        }
    }

    /**
     * 判断值是否超出插值区间。
     *
     * @param value 待检查数值
     * @param start 区间起点
     * @param end 区间终点
     * @return 是否越界
     */
    private static boolean outOfRange(double value, double start, double end) {
        return value < start || value > end;
    }
}
