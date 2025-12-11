package com.icbo.research;

import com.icbo.research.utils.StatisticalTest;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

/**
 * Test Statistical Analysis功能测试
 *
 * 使用已有的实验结果CSV来测试统计检验功能
 */
public class TestStatisticalAnalysis {
    public static void main(String[] args) {
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║   统计检验功能测试                                              ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");

        // 读取CSV文件
        String csvFile = "ICBO_RawData_20251210_210602.csv";
        Map<String, Map<String, List<Double>>> results = readCSV(csvFile);

        if (results.isEmpty()) {
            System.err.println("✗ 无法读取数据文件: " + csvFile);
            return;
        }

        // 显示数据统计
        System.out.println("\n读取的数据统计：");
        for (String algorithm : results.keySet()) {
            for (String scale : results.get(algorithm).keySet()) {
                int count = results.get(algorithm).get(scale).size();
                System.out.println(String.format("  %s - %s: %d个数据点", algorithm, scale, count));
            }
        }

        // 测试统计检验功能
        System.out.println("\n测试统计检验导出功能...");
        ResultWriter.exportStatisticalComparison(
                "ICBO_StatisticalTests_TEST.csv",
                results,
                "CBO",
                "ICBO-Enhanced"
        );

        // 打印统计检验摘要
        ResultWriter.printStatisticalSummary(results, "CBO", "ICBO-Enhanced");

        System.out.println("\n✅ 统计检验功能测试完成！");
        System.out.println("请检查生成的文件: ICBO_StatisticalTests_TEST.csv");
    }

    /**
     * 读取CSV文件
     * 格式: Algorithm,Scale,Run,Makespan
     */
    private static Map<String, Map<String, List<Double>>> readCSV(String filename) {
        Map<String, Map<String, List<Double>>> results = new LinkedHashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            reader.readLine(); // 跳过表头

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 4) continue;

                String algorithm = parts[0].trim();
                String scale = parts[1].trim();
                double makespan = Double.parseDouble(parts[3].trim());

                results.putIfAbsent(algorithm, new LinkedHashMap<>());
                results.get(algorithm).putIfAbsent(scale, new ArrayList<>());
                results.get(algorithm).get(scale).add(makespan);
            }

            System.out.println("✓ 成功读取数据文件: " + filename);

        } catch (Exception e) {
            System.err.println("✗ 读取CSV文件失败: " + e.getMessage());
            e.printStackTrace();
        }

        return results;
    }
}
