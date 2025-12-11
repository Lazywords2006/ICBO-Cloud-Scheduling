package com.icbo.research;

import java.util.Arrays;
import java.util.List;

/**
 * Metrics Calculator for Algorithm Performance Analysis
 *
 * 统计分析工具 - 用于计算算法性能指标
 *
 * 支持的统计指标：
 * - 平均值 (Mean)
 * - 标准差 (Standard Deviation)
 * - 最小值 (Min)
 * - 最大值 (Max)
 * - 中位数 (Median)
 * - 变异系数 (Coefficient of Variation)
 */
public class MetricsCalculator {

    /**
     * 计算平均值
     */
    public static double calculateMean(double[] values) {
        if (values == null || values.length == 0) {
            return 0.0;
        }
        return Arrays.stream(values).average().orElse(0.0);
    }

    /**
     * 计算平均值（List版本）
     */
    public static double calculateMean(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    /**
     * 计算标准差
     */
    public static double calculateStd(double[] values) {
        if (values == null || values.length == 0) {
            return 0.0;
        }

        double mean = calculateMean(values);
        double variance = 0.0;

        for (double value : values) {
            variance += Math.pow(value - mean, 2);
        }

        variance /= values.length;
        return Math.sqrt(variance);
    }

    /**
     * 计算标准差（List版本）
     */
    public static double calculateStd(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        double[] array = values.stream().mapToDouble(Double::doubleValue).toArray();
        return calculateStd(array);
    }

    /**
     * 计算最小值
     */
    public static double calculateMin(double[] values) {
        if (values == null || values.length == 0) {
            return 0.0;
        }
        return Arrays.stream(values).min().orElse(0.0);
    }

    /**
     * 计算最小值（List版本）
     */
    public static double calculateMin(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        return values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
    }

    /**
     * 计算最大值
     */
    public static double calculateMax(double[] values) {
        if (values == null || values.length == 0) {
            return 0.0;
        }
        return Arrays.stream(values).max().orElse(0.0);
    }

    /**
     * 计算最大值（List版本）
     */
    public static double calculateMax(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        return values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    }

    /**
     * 计算中位数
     */
    public static double calculateMedian(double[] values) {
        if (values == null || values.length == 0) {
            return 0.0;
        }

        double[] sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);

        int n = sorted.length;
        if (n % 2 == 0) {
            return (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
        } else {
            return sorted[n / 2];
        }
    }

    /**
     * 计算中位数（List版本）
     */
    public static double calculateMedian(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        double[] array = values.stream().mapToDouble(Double::doubleValue).toArray();
        return calculateMedian(array);
    }

    /**
     * 计算变异系数 (CV = Std / Mean)
     * 用于衡量算法的稳定性
     */
    public static double calculateCV(double[] values) {
        double mean = calculateMean(values);
        if (mean == 0.0) {
            return 0.0;
        }
        double std = calculateStd(values);
        return std / mean;
    }

    /**
     * 计算变异系数（List版本）
     */
    public static double calculateCV(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        double[] array = values.stream().mapToDouble(Double::doubleValue).toArray();
        return calculateCV(array);
    }

    /**
     * 计算改进率 (Improvement Rate)
     * @param baseline 基准值
     * @param improved 改进值
     * @return 改进率百分比（正值表示改进）
     */
    public static double calculateImprovement(double baseline, double improved) {
        if (baseline == 0.0) {
            return 0.0;
        }
        return ((baseline - improved) / baseline) * 100.0;
    }

    /**
     * 打印完整的统计摘要
     */
    public static void printSummary(String algorithmName, double[] values) {
        System.out.println("\n==================== " + algorithmName + " 统计摘要 ====================");
        System.out.println(String.format("平均值 (Mean):    %.4f", calculateMean(values)));
        System.out.println(String.format("标准差 (Std):     %.4f", calculateStd(values)));
        System.out.println(String.format("最小值 (Min):     %.4f", calculateMin(values)));
        System.out.println(String.format("最大值 (Max):     %.4f", calculateMax(values)));
        System.out.println(String.format("中位数 (Median):  %.4f", calculateMedian(values)));
        System.out.println(String.format("变异系数 (CV):    %.4f", calculateCV(values)));
        System.out.println("========================================================================");
    }

    /**
     * 打印完整的统计摘要（List版本）
     */
    public static void printSummary(String algorithmName, List<Double> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        double[] array = values.stream().mapToDouble(Double::doubleValue).toArray();
        printSummary(algorithmName, array);
    }

    /**
     * 统计结果类
     */
    public static class Statistics {
        public double mean;
        public double std;
        public double min;
        public double max;
        public double median;
        public double cv;

        public Statistics(double[] values) {
            this.mean = calculateMean(values);
            this.std = calculateStd(values);
            this.min = calculateMin(values);
            this.max = calculateMax(values);
            this.median = calculateMedian(values);
            this.cv = calculateCV(values);
        }

        public Statistics(List<Double> values) {
            this(values.stream().mapToDouble(Double::doubleValue).toArray());
        }

        @Override
        public String toString() {
            return String.format("Mean=%.4f, Std=%.4f, Min=%.4f, Max=%.4f, Median=%.4f, CV=%.4f",
                    mean, std, min, max, median, cv);
        }
    }
}
