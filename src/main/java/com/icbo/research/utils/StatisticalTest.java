package com.icbo.research.utils;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.inference.MannWhitneyUTest;

import java.util.List;

/**
 * StatisticalTest - 统计检验工具类
 *
 * 提供算法性能对比的统计显著性检验
 *
 * 功能：
 * 1. Wilcoxon秩和检验（Mann-Whitney U Test）- 非参数检验
 * 2. Cohen's d效应量 - 衡量差异大小
 *
 * 使用场景：
 * - 验证ICBO-Enhanced相对CBO的改进是否统计显著
 * - 计算效应量以评估改进的实际意义
 *
 * 参考标准：
 * - p-value < 0.05: 显著差异
 * - p-value < 0.01: 高度显著差异
 * - Cohen's d > 0.5: 中等效应
 * - Cohen's d > 0.8: 大效应
 */
public class StatisticalTest {

    /**
     * Wilcoxon秩和检验（Mann-Whitney U Test）
     *
     * 非参数检验，不假设数据分布，适合云调度实验数据
     *
     * @param baseline 基准算法结果（如CBO）
     * @param improved 改进算法结果（如ICBO-Enhanced）
     * @return p-value（<0.05表示显著差异）
     */
    public static double wilcoxonTest(List<Double> baseline, List<Double> improved) {
        if (baseline.isEmpty() || improved.isEmpty()) {
            return 1.0; // 无数据时返回1.0（不显著）
        }

        // 转换为double数组
        double[] baselineArray = baseline.stream().mapToDouble(Double::doubleValue).toArray();
        double[] improvedArray = improved.stream().mapToDouble(Double::doubleValue).toArray();

        // 执行Mann-Whitney U检验
        MannWhitneyUTest test = new MannWhitneyUTest();
        return test.mannWhitneyUTest(baselineArray, improvedArray);
    }

    /**
     * Cohen's d效应量
     *
     * 衡量两组数据的标准化差异大小
     *
     * 效应量解释：
     * - d < 0.2: 可忽略效应
     * - 0.2 <= d < 0.5: 小效应
     * - 0.5 <= d < 0.8: 中等效应
     * - d >= 0.8: 大效应
     *
     * @param baseline 基准算法结果（如CBO）
     * @param improved 改进算法结果（如ICBO-Enhanced）
     * @return Cohen's d值（>0表示改进算法更好）
     */
    public static double cohensD(List<Double> baseline, List<Double> improved) {
        if (baseline.isEmpty() || improved.isEmpty()) {
            return 0.0;
        }

        // 计算均值
        DescriptiveStatistics baselineStats = new DescriptiveStatistics();
        DescriptiveStatistics improvedStats = new DescriptiveStatistics();

        baseline.forEach(baselineStats::addValue);
        improved.forEach(improvedStats::addValue);

        double baselineMean = baselineStats.getMean();
        double improvedMean = improvedStats.getMean();

        // 计算合并标准差（pooled standard deviation）
        double baselineVar = baselineStats.getVariance();
        double improvedVar = improvedStats.getVariance();
        int n1 = baseline.size();
        int n2 = improved.size();

        double pooledStd = Math.sqrt(((n1 - 1) * baselineVar + (n2 - 1) * improvedVar) / (n1 + n2 - 2));

        // Cohen's d = (M1 - M2) / pooled_std
        // 注意：对于最小化问题（如Makespan），improved < baseline时d为正
        return (baselineMean - improvedMean) / pooledStd;
    }

    /**
     * 效应量解释
     *
     * @param cohensD Cohen's d值
     * @return 效应量描述
     */
    public static String interpretCohensD(double cohensD) {
        double absD = Math.abs(cohensD);
        if (absD < 0.2) {
            return "negligible";  // 可忽略
        } else if (absD < 0.5) {
            return "small";       // 小效应
        } else if (absD < 0.8) {
            return "medium";      // 中等效应
        } else {
            return "large";       // 大效应
        }
    }

    /**
     * p-value解释
     *
     * @param pValue p-value
     * @return 显著性描述
     */
    public static String interpretPValue(double pValue) {
        if (pValue < 0.001) {
            return "***";  // 高度显著
        } else if (pValue < 0.01) {
            return "**";   // 很显著
        } else if (pValue < 0.05) {
            return "*";    // 显著
        } else {
            return "ns";   // 不显著
        }
    }

    /**
     * 判断是否显著
     *
     * @param pValue p-value
     * @return true if significant (p < 0.05)
     */
    public static boolean isSignificant(double pValue) {
        return pValue < 0.05;
    }
}
