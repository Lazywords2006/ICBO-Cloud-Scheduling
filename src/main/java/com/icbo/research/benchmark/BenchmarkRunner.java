package com.icbo.research.benchmark;

import com.icbo.research.benchmark.functions.*;

import java.util.ArrayList;
import java.util.List;

/**
 * CEC2017基准测试运行器
 *
 * 提供统一接口运行基准函数测试，支持：
 * - 多函数批量测试
 * - 多算法对比
 * - 多次独立运行
 * - 统计结果记录
 *
 * @author ICBO Research Team
 * @version 1.0
 * @date 2025-12-10
 */
public class BenchmarkRunner {

    /**
     * 获取所有CEC2017基准函数（10个）
     *
     * @return 函数列表
     */
    public static List<BenchmarkFunction> getAllFunctions() {
        List<BenchmarkFunction> functions = new ArrayList<>();

        functions.add(new Sphere(30));           // F1
        functions.add(new Rosenbrock(30));       // F2
        functions.add(new Rastrigin(30));        // F3
        functions.add(new Griewank(30));         // F4
        functions.add(new SixHumpCamel());       // F5 (2D)
        functions.add(new Ackley(30));           // F6
        functions.add(new Schwefel(30));         // F7
        functions.add(new Zakharov(30));         // F8
        functions.add(new SumSquares(30));       // F9
        functions.add(new Powell(28));           // F10 (28D, 4的倍数)

        return functions;
    }

    /**
     * 获取标准CEC函数子集（用于快速验证）
     * 包含：Sphere, Rastrigin, Ackley（3个代表性函数）
     *
     * @return 函数列表
     */
    public static List<BenchmarkFunction> getQuickTestFunctions() {
        List<BenchmarkFunction> functions = new ArrayList<>();

        functions.add(new Sphere(30));           // F1 - 单峰
        functions.add(new Rastrigin(30));        // F3 - 多峰
        functions.add(new Ackley(30));           // F6 - 复杂多峰

        return functions;
    }

    /**
     * 单个函数单次运行
     *
     * @param function 基准函数
     * @param optimizer 优化器接口（算法实现）
     * @param maxIterations 最大迭代次数
     * @return 最终适应度值
     */
    public static double runSingleTest(BenchmarkFunction function,
                                      BenchmarkOptimizer optimizer,
                                      int maxIterations) {
        // 调用优化器执行优化
        double bestFitness = optimizer.optimize(function, maxIterations);

        return bestFitness;
    }

    /**
     * 单个函数多次独立运行
     *
     * @param function 基准函数
     * @param optimizer 优化器接口
     * @param maxIterations 最大迭代次数
     * @param numRuns 独立运行次数
     * @return 结果统计对象
     */
    public static BenchmarkResult runMultipleTests(BenchmarkFunction function,
                                                  BenchmarkOptimizer optimizer,
                                                  int maxIterations,
                                                  int numRuns) {
        List<Double> results = new ArrayList<>();

        System.out.println(String.format("\n[%s] Testing %s on %s (%d runs, %d iterations)",
                                        optimizer.getName(),
                                        optimizer.getName(),
                                        function.getName(),
                                        numRuns,
                                        maxIterations));

        for (int run = 0; run < numRuns; run++) {
            double fitness = runSingleTest(function, optimizer, maxIterations);
            results.add(fitness);

            // 每5次运行输出一次进度
            if ((run + 1) % 5 == 0 || run == numRuns - 1) {
                System.out.println(String.format("  Run %d/%d: Best=%.6f",
                                                run + 1, numRuns, fitness));
            }
        }

        return new BenchmarkResult(
            function.getName(),
            optimizer.getName(),
            maxIterations,
            numRuns,
            results
        );
    }

    /**
     * 优化器接口（算法需要实现此接口）
     */
    public interface BenchmarkOptimizer {
        /**
         * 执行优化
         *
         * @param function 基准函数
         * @param maxIterations 最大迭代次数
         * @return 最优适应度值
         */
        double optimize(BenchmarkFunction function, int maxIterations);

        /**
         * 获取算法名称
         *
         * @return 算法名称
         */
        String getName();
    }

    /**
     * 基准测试结果
     */
    public static class BenchmarkResult {
        private final String functionName;
        private final String algorithmName;
        private final int maxIterations;
        private final int numRuns;
        private final List<Double> rawResults;

        // 统计指标
        private final double avgFitness;
        private final double stdFitness;
        private final double minFitness;
        private final double maxFitness;
        private final double medianFitness;

        public BenchmarkResult(String functionName, String algorithmName,
                              int maxIterations, int numRuns,
                              List<Double> rawResults) {
            this.functionName = functionName;
            this.algorithmName = algorithmName;
            this.maxIterations = maxIterations;
            this.numRuns = numRuns;
            this.rawResults = new ArrayList<>(rawResults);

            // 计算统计指标
            this.avgFitness = calculateAverage(rawResults);
            this.stdFitness = calculateStdDev(rawResults, avgFitness);
            this.minFitness = rawResults.stream().min(Double::compare).orElse(0.0);
            this.maxFitness = rawResults.stream().max(Double::compare).orElse(0.0);
            this.medianFitness = calculateMedian(rawResults);
        }

        private double calculateAverage(List<Double> values) {
            return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }

        private double calculateStdDev(List<Double> values, double mean) {
            double variance = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);
            return Math.sqrt(variance);
        }

        private double calculateMedian(List<Double> values) {
            List<Double> sorted = new ArrayList<>(values);
            sorted.sort(Double::compare);
            int n = sorted.size();
            if (n % 2 == 0) {
                return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
            } else {
                return sorted.get(n / 2);
            }
        }

        // Getters

        public String getFunctionName() {
            return functionName;
        }

        public String getAlgorithmName() {
            return algorithmName;
        }

        public int getMaxIterations() {
            return maxIterations;
        }

        public int getNumRuns() {
            return numRuns;
        }

        public List<Double> getRawResults() {
            return new ArrayList<>(rawResults);
        }

        public double getAvgFitness() {
            return avgFitness;
        }

        public double getStdFitness() {
            return stdFitness;
        }

        public double getMinFitness() {
            return minFitness;
        }

        public double getMaxFitness() {
            return maxFitness;
        }

        public double getMedianFitness() {
            return medianFitness;
        }

        /**
         * 打印结果摘要
         */
        public void printSummary() {
            System.out.println(String.format("\n--- %s on %s (Iter=%d, Runs=%d) ---",
                                            algorithmName, functionName, maxIterations, numRuns));
            System.out.println(String.format("Average: %.6e", avgFitness));
            System.out.println(String.format("StdDev:  %.6e", stdFitness));
            System.out.println(String.format("Min:     %.6e", minFitness));
            System.out.println(String.format("Max:     %.6e", maxFitness));
            System.out.println(String.format("Median:  %.6e", medianFitness));
        }

        /**
         * 转换为CSV行（用于结果写入）
         *
         * @return CSV格式字符串
         */
        public String toCSVRow() {
            return String.format("%s,%s,%d,%d,%.6e,%.6e,%.6e,%.6e,%.6e",
                                functionName, algorithmName, maxIterations, numRuns,
                                avgFitness, stdFitness, minFitness, maxFitness, medianFitness);
        }

        /**
         * CSV头部
         *
         * @return CSV头部字符串
         */
        public static String getCSVHeader() {
            return "Function,Algorithm,MaxIterations,NumRuns,AvgFitness,StdFitness,MinFitness,MaxFitness,MedianFitness";
        }
    }

    /**
     * 打印所有函数信息
     */
    public static void printAllFunctions() {
        System.out.println("\n========== CEC2017 Benchmark Functions ==========");
        List<BenchmarkFunction> functions = getAllFunctions();
        for (BenchmarkFunction func : functions) {
            System.out.println("\n" + func.getDescription());
        }
        System.out.println("\n=================================================\n");
    }
}
