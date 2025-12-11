package com.icbo.research;

import com.icbo.research.utils.StatisticalTest;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Result Writer for Exporting Experimental Results
 *
 * 结果输出工具 - 将实验结果导出为CSV文件
 *
 * 功能：
 * - 导出原始数据（Raw Data）
 * - 导出统计摘要（Statistics Summary）
 * - 导出算法排名（Algorithm Ranking）
 * - 导出对比表格（Comparison Table）
 */
public class ResultWriter {

    /**
     * 导出原始数据到CSV
     * @param filename 文件名
     * @param results 结果Map：算法名 -> (规模名 -> makespan列表)
     */
    public static void exportRawData(String filename,
                                     Map<String, Map<String, List<Double>>> results) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            // 写入表头
            writer.write("Algorithm,Scale,Run,Makespan\n");

            // 写入数据
            for (String algorithm : results.keySet()) {
                Map<String, List<Double>> scaleResults = results.get(algorithm);
                for (String scale : scaleResults.keySet()) {
                    List<Double> makespans = scaleResults.get(scale);
                    for (int i = 0; i < makespans.size(); i++) {
                        writer.write(String.format("%s,%s,%d,%.6f\n",
                                algorithm, scale, i + 1, makespans.get(i)));
                    }
                }
            }

            System.out.println("\n✓ 原始数据已导出到: " + filename);

        } catch (IOException e) {
            System.err.println("✗ 导出原始数据失败: " + e.getMessage());
        }
    }

    /**
     * 导出统计摘要到CSV（仿照ERTH论文表格格式）
     * @param filename 文件名
     * @param results 结果Map：算法名 -> (规模名 -> makespan列表)
     */
    public static void exportStatisticsSummary(String filename,
                                               Map<String, Map<String, List<Double>>> results) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            // 获取所有规模（假设所有算法的规模相同）
            List<String> scales = new ArrayList<>(
                results.values().iterator().next().keySet()
            );
            Collections.sort(scales);

            // 写入表头
            writer.write("Algorithm");
            for (String scale : scales) {
                writer.write("," + scale + "_Mean");
                writer.write("," + scale + "_Std");
            }
            writer.write(",Avg_Rank\n");

            // 计算每个算法在每个规模下的统计值
            Map<String, List<Double>> meansByAlgorithm = new LinkedHashMap<>();
            Map<String, List<Double>> stdsByAlgorithm = new LinkedHashMap<>();

            for (String algorithm : results.keySet()) {
                List<Double> means = new ArrayList<>();
                List<Double> stds = new ArrayList<>();

                for (String scale : scales) {
                    List<Double> makespans = results.get(algorithm).get(scale);
                    double mean = MetricsCalculator.calculateMean(makespans);
                    double std = MetricsCalculator.calculateStd(makespans);
                    means.add(mean);
                    stds.add(std);
                }

                meansByAlgorithm.put(algorithm, means);
                stdsByAlgorithm.put(algorithm, stds);
            }

            // 计算排名
            Map<String, Double> avgRanks = calculateAverageRanks(meansByAlgorithm, scales);

            // 写入数据
            for (String algorithm : results.keySet()) {
                writer.write(algorithm);

                List<Double> means = meansByAlgorithm.get(algorithm);
                List<Double> stds = stdsByAlgorithm.get(algorithm);

                for (int i = 0; i < scales.size(); i++) {
                    writer.write(String.format(",%.4f", means.get(i)));
                    writer.write(String.format(",%.4f", stds.get(i)));
                }

                writer.write(String.format(",%.2f\n", avgRanks.get(algorithm)));
            }

            System.out.println("✓ 统计摘要已导出到: " + filename);

        } catch (IOException e) {
            System.err.println("✗ 导出统计摘要失败: " + e.getMessage());
        }
    }

    /**
     * 导出对比表格（简化版，用于快速查看）
     * @param filename 文件名
     * @param results 结果Map
     */
    public static void exportComparisonTable(String filename,
                                             Map<String, Map<String, List<Double>>> results) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            // 获取所有规模
            List<String> scales = new ArrayList<>(
                results.values().iterator().next().keySet()
            );
            Collections.sort(scales);

            // 写入表头
            writer.write("Algorithm");
            for (String scale : scales) {
                writer.write("," + scale);
            }
            writer.write(",Rank\n");

            // 计算均值
            Map<String, List<Double>> meansByAlgorithm = new LinkedHashMap<>();
            for (String algorithm : results.keySet()) {
                List<Double> means = new ArrayList<>();
                for (String scale : scales) {
                    List<Double> makespans = results.get(algorithm).get(scale);
                    means.add(MetricsCalculator.calculateMean(makespans));
                }
                meansByAlgorithm.put(algorithm, means);
            }

            // 计算排名
            Map<String, Double> avgRanks = calculateAverageRanks(meansByAlgorithm, scales);

            // 写入数据
            for (String algorithm : results.keySet()) {
                writer.write(algorithm);
                List<Double> means = meansByAlgorithm.get(algorithm);
                for (double mean : means) {
                    writer.write(String.format(",%.4f", mean));
                }
                writer.write(String.format(",%.2f\n", avgRanks.get(algorithm)));
            }

            System.out.println("✓ 对比表格已导出到: " + filename);

        } catch (IOException e) {
            System.err.println("✗ 导出对比表格失败: " + e.getMessage());
        }
    }

    /**
     * 计算平均排名
     */
    private static Map<String, Double> calculateAverageRanks(
            Map<String, List<Double>> meansByAlgorithm, List<String> scales) {

        Map<String, Double> avgRanks = new LinkedHashMap<>();
        int numScales = scales.size();

        // 为每个规模计算排名
        for (int i = 0; i < numScales; i++) {
            final int scaleIndex = i;

            // 获取该规模下所有算法的均值
            List<Map.Entry<String, Double>> scaleResults = new ArrayList<>();
            for (Map.Entry<String, List<Double>> entry : meansByAlgorithm.entrySet()) {
                scaleResults.add(new AbstractMap.SimpleEntry<>(
                        entry.getKey(), entry.getValue().get(scaleIndex)));
            }

            // 按Makespan排序（越小越好）
            scaleResults.sort(Comparator.comparingDouble(Map.Entry::getValue));

            // 分配排名
            for (int rank = 0; rank < scaleResults.size(); rank++) {
                String algorithm = scaleResults.get(rank).getKey();
                avgRanks.put(algorithm, avgRanks.getOrDefault(algorithm, 0.0) + (rank + 1));
            }
        }

        // 计算平均排名
        for (String algorithm : avgRanks.keySet()) {
            avgRanks.put(algorithm, avgRanks.get(algorithm) / numScales);
        }

        return avgRanks;
    }

    /**
     * 打印对比表格到控制台
     */
    public static void printComparisonTable(Map<String, Map<String, List<Double>>> results) {
        System.out.println("\n==================== 性能对比表格（Mean ± Std） ====================");

        // 获取所有规模
        List<String> scales = new ArrayList<>(
            results.values().iterator().next().keySet()
        );
        Collections.sort(scales);

        // 打印表头
        System.out.print(String.format("%-12s", "Algorithm"));
        for (String scale : scales) {
            System.out.print(String.format(" | %-18s", scale));
        }
        System.out.println(" | Rank");
        System.out.println("-".repeat(15 + scales.size() * 22 + 8));

        // 计算统计值和排名
        Map<String, List<Double>> meansByAlgorithm = new LinkedHashMap<>();
        for (String algorithm : results.keySet()) {
            List<Double> means = new ArrayList<>();
            for (String scale : scales) {
                List<Double> makespans = results.get(algorithm).get(scale);
                means.add(MetricsCalculator.calculateMean(makespans));
            }
            meansByAlgorithm.put(algorithm, means);
        }

        Map<String, Double> avgRanks = calculateAverageRanks(meansByAlgorithm, scales);

        // 打印数据
        for (String algorithm : results.keySet()) {
            System.out.print(String.format("%-12s", algorithm));

            for (String scale : scales) {
                List<Double> makespans = results.get(algorithm).get(scale);
                double mean = MetricsCalculator.calculateMean(makespans);
                double std = MetricsCalculator.calculateStd(makespans);
                System.out.print(String.format(" | %7.4f ± %6.4f", mean, std));
            }

            System.out.println(String.format(" | %.2f", avgRanks.get(algorithm)));
        }

        System.out.println("========================================================================\n");
    }

    /**
     * 生成带时间戳的文件名
     */
    public static String generateFilename(String prefix, String suffix) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = sdf.format(new Date());
        return String.format("%s_%s.%s", prefix, timestamp, suffix);
    }

    /**
     * 导出统计检验对比表（Statistical Comparison Table）
     *
     * 针对每个规模，计算ICBO-Enhanced相对CBO的统计显著性：
     * - Wilcoxon秩和检验 p-value
     * - Cohen's d效应量
     * - 显著性标记 (*, **, ***)
     * - 效应量解释 (small/medium/large)
     *
     * 解决Peer Review Critical问题：证明ICBO-Enhanced的改进具有统计显著性
     *
     * @param filename 文件名
     * @param results 结果Map：算法名 -> (规模名 -> makespan列表)
     * @param baselineAlgorithm 基准算法（通常为"CBO"）
     * @param improvedAlgorithm 改进算法（通常为"ICBO-Enhanced"）
     */
    public static void exportStatisticalComparison(String filename,
                                                   Map<String, Map<String, List<Double>>> results,
                                                   String baselineAlgorithm,
                                                   String improvedAlgorithm) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            // 检查算法是否存在
            if (!results.containsKey(baselineAlgorithm)) {
                System.err.println("✗ 基准算法不存在: " + baselineAlgorithm);
                return;
            }
            if (!results.containsKey(improvedAlgorithm)) {
                System.err.println("✗ 改进算法不存在: " + improvedAlgorithm);
                return;
            }

            // 获取所有规模
            List<String> scales = new ArrayList<>(results.get(baselineAlgorithm).keySet());
            Collections.sort(scales);

            // 写入表头
            writer.write("Scale,BaselineMean,BaselineStd,ImprovedMean,ImprovedStd,Improvement%,PValue,Significant,CohensD,EffectSize\n");

            // 对每个规模进行统计检验
            for (String scale : scales) {
                List<Double> baselineData = results.get(baselineAlgorithm).get(scale);
                List<Double> improvedData = results.get(improvedAlgorithm).get(scale);

                // 计算基本统计量
                double baselineMean = MetricsCalculator.calculateMean(baselineData);
                double baselineStd = MetricsCalculator.calculateStd(baselineData);
                double improvedMean = MetricsCalculator.calculateMean(improvedData);
                double improvedStd = MetricsCalculator.calculateStd(improvedData);

                // 计算改进率
                double improvement = MetricsCalculator.calculateImprovement(baselineMean, improvedMean);

                // 计算统计检验指标
                double pValue = StatisticalTest.wilcoxonTest(baselineData, improvedData);
                String significant = StatisticalTest.interpretPValue(pValue);
                double cohensD = StatisticalTest.cohensD(baselineData, improvedData);
                String effectSize = StatisticalTest.interpretCohensD(cohensD);

                // 写入数据
                writer.write(String.format("%s,%.4f,%.4f,%.4f,%.4f,%.2f%%,%.6f,%s,%.4f,%s\n",
                        scale,
                        baselineMean, baselineStd,
                        improvedMean, improvedStd,
                        improvement,
                        pValue, significant,
                        cohensD, effectSize));
            }

            System.out.println("✓ 统计检验结果已导出到: " + filename);
            System.out.println("  包含: Wilcoxon p-value, Cohen's d, 显著性标记");

        } catch (IOException e) {
            System.err.println("✗ 导出统计检验失败: " + e.getMessage());
        }
    }

    /**
     * 打印统计检验摘要到控制台
     *
     * 展示ICBO-Enhanced vs CBO的统计显著性结果
     *
     * @param results 结果Map
     * @param baselineAlgorithm 基准算法
     * @param improvedAlgorithm 改进算法
     */
    public static void printStatisticalSummary(Map<String, Map<String, List<Double>>> results,
                                                String baselineAlgorithm,
                                                String improvedAlgorithm) {
        if (!results.containsKey(baselineAlgorithm) || !results.containsKey(improvedAlgorithm)) {
            return;
        }

        System.out.println("\n==================== 统计显著性检验结果 ====================");
        System.out.println(String.format("基准算法: %s  vs  改进算法: %s", baselineAlgorithm, improvedAlgorithm));
        System.out.println("-".repeat(70));

        List<String> scales = new ArrayList<>(results.get(baselineAlgorithm).keySet());
        Collections.sort(scales);

        int significantCount = 0;
        double totalImprovement = 0.0;

        for (String scale : scales) {
            List<Double> baselineData = results.get(baselineAlgorithm).get(scale);
            List<Double> improvedData = results.get(improvedAlgorithm).get(scale);

            double baselineMean = MetricsCalculator.calculateMean(baselineData);
            double improvedMean = MetricsCalculator.calculateMean(improvedData);
            double improvement = MetricsCalculator.calculateImprovement(baselineMean, improvedMean);
            double pValue = StatisticalTest.wilcoxonTest(baselineData, improvedData);
            double cohensD = StatisticalTest.cohensD(baselineData, improvedData);

            String sig = StatisticalTest.interpretPValue(pValue);
            String effect = StatisticalTest.interpretCohensD(cohensD);

            System.out.println(String.format("%-10s | 改进: %6.2f%% | p=%.4f %-3s | d=%.2f (%s)",
                    scale, improvement, pValue, sig, cohensD, effect));

            if (StatisticalTest.isSignificant(pValue)) {
                significantCount++;
            }
            totalImprovement += improvement;
        }

        System.out.println("-".repeat(70));
        System.out.println(String.format("平均改进率: %.2f%%  |  显著规模数: %d/%d",
                totalImprovement / scales.size(), significantCount, scales.size()));
        System.out.println("========================================================================\n");
    }
}

