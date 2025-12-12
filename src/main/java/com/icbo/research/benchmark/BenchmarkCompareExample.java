package com.icbo.research.benchmark;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * CEC2017基准测试批量对比实验
 *
 * 测试配置：
 * - 7个算法：Random, PSO, GWO, WOA, CBO, ICBO, ICBO-Enhanced
 * - 30个函数：F1-F30（完整CEC2017函数集）
 * - 30次独立运行（CEC2017标准配置）
 *
 * 实验规模：
 * - 快速验证：7算法 × 3函数 × 5次 = 105次测试（~10分钟）
 * - 完整实验：7算法 × 30函数 × 30次 = 6300次测试（~15-20小时）
 *
 * 输出格式：
 * - 统计数据CSV：算法×函数的平均值、标准差等
 * - 原始数据CSV：每次运行的详细结果
 * - 对比报告MD：Markdown格式的结果表格
 *
 * @author ICBO Research Team
 * @version 3.0 (完整7算法版本)
 * @date 2025-12-11
 */
public class BenchmarkCompareExample {

    // 实验配置
    private static final int MAX_ITERATIONS = 1000;  // CEC2017标准迭代次数
    private static final int NUM_RUNS = 30;          // 独立运行次数（完整实验）
    private static final int QUICK_NUM_RUNS = 5;     // 快速验证运行次数

    /**
     * 主函数 - 运行完整实验
     */
    public static void main(String[] args) {
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║  CEC2017 Benchmark Test - CBO vs ICBO vs ICBO-Enhanced      ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

        // 选择实验模式
        boolean quickMode = false;  // true=快速验证（45次测试），false=完整实验（2700次测试）

        if (quickMode) {
            runQuickTest();
        } else {
            runFullExperiment();
        }
    }

    /**
     * 运行快速验证测试
     * 7算法 × 3函数 × 5次 = 105次测试
     */
    public static void runQuickTest() {
        System.out.println("【快速验证模式】\n");
        System.out.println("测试配置：");
        System.out.println("  - 算法：Random, PSO, GWO, WOA, CBO, ICBO, ICBO-Enhanced");
        System.out.println("  - 函数：Sphere, Rastrigin, Ackley（代表性函数）");
        System.out.println("  - 运行次数：" + QUICK_NUM_RUNS);
        System.out.println("  - 迭代次数：" + MAX_ITERATIONS);
        System.out.println("  - 总测试量：7 × 3 × " + QUICK_NUM_RUNS + " = " + (7 * 3 * QUICK_NUM_RUNS) + " 次\n");

        // 创建算法列表（按性能预期排序：基线→成熟算法→改进算法）
        List<BenchmarkRunner.BenchmarkOptimizer> algorithms = new ArrayList<>();
        algorithms.add(new Random_Lite());     // 基线算法
        algorithms.add(new PSO_Lite());        // 成熟元启发式
        algorithms.add(new GWO_Lite());        // 成熟元启发式
        algorithms.add(new WOA_Lite());        // 成熟元启发式
        algorithms.add(new CBO_Lite());        // 基准CBO
        algorithms.add(new ICBO_Lite());       // ICBO改进
        algorithms.add(new ICBO_E_Lite());     // ICBO增强版

        // 获取快速测试函数（3个代表性函数）
        List<BenchmarkFunction> functions = BenchmarkRunner.getQuickTestFunctions();

        // 运行实验
        List<BenchmarkRunner.BenchmarkResult> allResults = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        for (BenchmarkFunction function : functions) {
            System.out.println("\n========================================");
            System.out.println("Testing: " + function.getName());
            System.out.println("========================================");

            for (BenchmarkRunner.BenchmarkOptimizer algorithm : algorithms) {
                BenchmarkRunner.BenchmarkResult result = BenchmarkRunner.runMultipleTests(
                    function, algorithm, MAX_ITERATIONS, QUICK_NUM_RUNS
                );
                result.printSummary();
                allResults.add(result);
            }
        }

        long endTime = System.currentTimeMillis();
        double elapsedMinutes = (endTime - startTime) / 60000.0;

        // 保存结果
        try {
            String baseFilename = "CEC2017_QuickTest";
            BenchmarkResultWriter.writeAllFormats(allResults, baseFilename);
            System.out.println("\n✅ 快速验证完成！用时: " + String.format("%.2f", elapsedMinutes) + " 分钟");
        } catch (IOException e) {
            System.err.println("❌ 结果保存失败: " + e.getMessage());
        }
    }

    /**
     * 运行完整实验
     * 7算法 × 30函数 × 30次 = 6300次测试
     */
    public static void runFullExperiment() {
        System.out.println("【完整实验模式】\n");
        System.out.println("测试配置：");
        System.out.println("  - 算法：Random, PSO, GWO, WOA, CBO, ICBO, ICBO-Enhanced");
        System.out.println("  - 函数：F1-F30（全部30个CEC2017函数）");
        System.out.println("  - 运行次数：" + NUM_RUNS);
        System.out.println("  - 迭代次数：" + MAX_ITERATIONS);
        System.out.println("  - 总测试量：7 × 30 × " + NUM_RUNS + " = " + (7 * 30 * NUM_RUNS) + " 次");
        System.out.println("  - 预计用时：15-20 小时\n");
        System.out.println("警告：这将是一个长时间运行的实验！\n");

        // 创建算法列表（按性能预期排序：基线→成熟算法→改进算法）
        List<BenchmarkRunner.BenchmarkOptimizer> algorithms = new ArrayList<>();
        algorithms.add(new Random_Lite());     // 基线算法
        algorithms.add(new PSO_Lite());        // 成熟元启发式
        algorithms.add(new GWO_Lite());        // 成熟元启发式
        algorithms.add(new WOA_Lite());        // 成熟元启发式
        algorithms.add(new CBO_Lite());        // 基准CBO
        algorithms.add(new ICBO_Lite());       // ICBO改进
        algorithms.add(new ICBO_E_Lite());     // ICBO增强版

        // 获取全部CEC2017函数
        List<BenchmarkFunction> functions = BenchmarkRunner.getAllFunctions();

        // 运行实验
        List<BenchmarkRunner.BenchmarkResult> allResults = new ArrayList<>();

        long startTime = System.currentTimeMillis();
        int totalTests = algorithms.size() * functions.size();
        int completedTests = 0;

        for (BenchmarkFunction function : functions) {
            System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║  Testing: " + String.format("%-50s", function.getName()) + " ║");
            System.out.println("╚════════════════════════════════════════════════════════════════╝");

            for (BenchmarkRunner.BenchmarkOptimizer algorithm : algorithms) {
                completedTests++;
                System.out.println(String.format("\n[进度: %d/%d] 算法: %s",
                                                completedTests, totalTests, algorithm.getName()));

                BenchmarkRunner.BenchmarkResult result = BenchmarkRunner.runMultipleTests(
                    function, algorithm, MAX_ITERATIONS, NUM_RUNS
                );
                result.printSummary();
                allResults.add(result);

                // 计算剩余时间估计
                long currentTime = System.currentTimeMillis();
                double elapsedMinutes = (currentTime - startTime) / 60000.0;
                double avgTimePerTest = elapsedMinutes / completedTests;
                double remainingTests = totalTests - completedTests;
                double estimatedRemainingMinutes = avgTimePerTest * remainingTests;

                System.out.println(String.format("已用时间: %.2f 分钟 | 预计剩余: %.2f 分钟",
                                                elapsedMinutes, estimatedRemainingMinutes));
            }
        }

        long endTime = System.currentTimeMillis();
        double elapsedMinutes = (endTime - startTime) / 60000.0;

        // 保存结果
        try {
            String baseFilename = "CEC2017_FullExperiment";
            BenchmarkResultWriter.writeAllFormats(allResults, baseFilename);

            System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║              实验完成！                                         ║");
            System.out.println("╚════════════════════════════════════════════════════════════════╝");
            System.out.println("总用时: " + String.format("%.2f", elapsedMinutes) + " 分钟 (" +
                              String.format("%.2f", elapsedMinutes / 60.0) + " 小时)");
            System.out.println("总测试次数: " + (algorithms.size() * functions.size() * NUM_RUNS));

            // 打印排名概览
            printRankingSummary(allResults, functions);

        } catch (IOException e) {
            System.err.println("❌ 结果保存失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 打印排名概览
     */
    private static void printRankingSummary(List<BenchmarkRunner.BenchmarkResult> allResults,
                                           List<BenchmarkFunction> functions) {
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║              算法排名概览                                       ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

        int[] algorithmWins = new int[7];  // Random, PSO, GWO, WOA, CBO, ICBO, ICBO-E
        String[] algorithmNames = {"Random", "PSO", "GWO", "WOA", "CBO", "ICBO", "ICBO-Enhanced"};

        for (BenchmarkFunction function : functions) {
            double[] bestFitness = new double[7];
            for (int i = 0; i < 7; i++) {
                String algorithmName = algorithmNames[i];
                for (BenchmarkRunner.BenchmarkResult result : allResults) {
                    if (result.getFunctionName().equals(function.getName()) &&
                        result.getAlgorithmName().equals(algorithmName)) {
                        bestFitness[i] = result.getAvgFitness();
                        break;
                    }
                }
            }

            // 找出最优算法
            int bestIdx = 0;
            for (int i = 1; i < 7; i++) {
                if (bestFitness[i] < bestFitness[bestIdx]) {
                    bestIdx = i;
                }
            }
            algorithmWins[bestIdx]++;

            System.out.println(String.format("%-20s: %s (%.6e)",
                                            function.getName(),
                                            algorithmNames[bestIdx],
                                            bestFitness[bestIdx]));
        }

        System.out.println("\n【总获胜次数】");
        for (int i = 0; i < 7; i++) {
            System.out.println(String.format("  %s: %d/%d 函数获胜",
                                            algorithmNames[i],
                                            algorithmWins[i],
                                            functions.size()));
        }
    }

    /**
     * 自定义实验配置（高级用法）
     */
    public static void runCustomExperiment(List<BenchmarkFunction> functions,
                                          List<BenchmarkRunner.BenchmarkOptimizer> algorithms,
                                          int maxIterations,
                                          int numRuns) {
        System.out.println("【自定义实验模式】\n");

        List<BenchmarkRunner.BenchmarkResult> allResults = new ArrayList<>();

        for (BenchmarkFunction function : functions) {
            for (BenchmarkRunner.BenchmarkOptimizer algorithm : algorithms) {
                BenchmarkRunner.BenchmarkResult result = BenchmarkRunner.runMultipleTests(
                    function, algorithm, maxIterations, numRuns
                );
                allResults.add(result);
            }
        }

        // 保存结果
        try {
            BenchmarkResultWriter.writeAllFormats(allResults, "CEC2017_Custom");
        } catch (IOException e) {
            System.err.println("结果保存失败: " + e.getMessage());
        }
    }
}
