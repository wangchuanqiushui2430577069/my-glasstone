package com.glasstone.math;

import org.hipparchus.distribution.continuous.NormalDistribution;
import org.hipparchus.special.Erf;
import org.hipparchus.special.Gamma;

/**
 * 对 Hipparchus 特殊函数的轻量封装。
 */
public final class SpecialFunctions {
    /**
     * 标准正态分布对象，供累计分布函数计算复用。
     */
    private static final NormalDistribution STANDARD_NORMAL = new NormalDistribution(0.0d, 1.0d);

    /**
     * 私有构造器，禁止实例化工具类。
     */
    private SpecialFunctions() {
    }

    /**
     * 计算 Gamma 函数值。
     *
     * @param z 自变量
     * @return Gamma(z)
     */
    public static double gamma(double z) {
        return Gamma.gamma(z);
    }

    /**
     * 计算对数 Gamma 函数值。
     *
     * @param z 自变量
     * @return log(Gamma(z))
     */
    public static double logGamma(double z) {
        return Gamma.logGamma(z);
    }

    /**
     * 计算标准正态分布的累计分布函数。
     *
     * @param x 自变量
     * @return P(X <= x)
     */
    public static double normalCdf(double x) {
        return STANDARD_NORMAL.cumulativeProbability(x);
    }

    /**
     * 计算误差函数。
     *
     * @param x 自变量
     * @return erf(x)
     */
    public static double erf(double x) {
        return Erf.erf(x);
    }
}
