package com.icbo.research;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * ✅ Phase 5: 参数敏感性分析测试
 *
 * 目的：验证k=3和λ=0.4是最优参数配置
 *
 * 测试参数：
 * - k（动态权重衰减指数）：{1, 2, 3, 4, 5}
 * - λ（Bernoulli混沌参数）：{0.2, 0.3, 0.4, 0.5, 0.6}
 * - 固定：ω_max=0.80, ω_min=0.10（这两个参数影响较小）
 *
 * 实验设置：
 * - 25组参数配置
 * - 每组：5个种子 × 5次运行 = 25次实验
 * - 测试规模：M=100任务，N=20 VMs
 * - 总实验次数：25 × 25 = 625次
 *
 * 输出：
 * - sensitivity_results.csv: k, lambda, meanMakespan, stdMakespan
 * - 用于绘制k×λ热力图
 *
 * @author ICBO Research Team
 * @date 2025-12-10
 */
public class ParameterSensitivityTest {

    // ==================== 实验配置 ====================
    private static final int M = 100;  // 任务数
    private static final int N = 20;   // VM数
    private static final int NUM_RUNS = 5;  // 每组参数的运行次数

    // 测试种子（使用5个不同种子以确保统计稳定性）
    private static final long[] TEST_SEEDS = {42L, 123L, 456L, 789L, 1024L};

    // 固定参数（基于已知最优值）
    private static final double OMEGA_MAX = 0.80;
    private static final double OMEGA_MIN = 0.10;

    // 测试参数范围
    private static final int[] K_VALUES = {1, 2, 3, 4, 5};
    private static final double[] LAMBDA_VALUES = {0.2, 0.3, 0.4, 0.5, 0.6};

    // ==================== 主函数 ====================

    public static void main(String[] args) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("              Phase 5: 参数敏感性分析实验");
        System.out.println("=".repeat(80));

        System.out.println("\n实验配置：");
        System.out.println("  任务数（M）: " + M);
        System.out.println("  VM数（N）: " + N);
        System.out.println("  k参数范围: " + Arrays.toString(K_VALUES));
        System.out.println("  λ参数范围: " + Arrays.toString(LAMBDA_VALUES));
        System.out.println("  固定参数: ω_max=" + OMEGA_MAX + ", ω_min=" + OMEGA_MIN);
        System.out.println("  测试种子数: " + TEST_SEEDS.length);
        System.out.println("  每组运行次数: " + NUM_RUNS);
        System.out.println("  总配置数: " + (K_VALUES.length * LAMBDA_VALUES.length));
        System.out.println("  总实验次数: " + (K_VALUES.length * LAMBDA_VALUES.length * TEST_SEEDS.length * NUM_RUNS));

        // 存储结果
        List<SensitivityResult> results = new ArrayList<>();

        int totalConfigs = K_VALUES.length * LAMBDA_VALUES.length;
        int currentConfig = 0;

        // 嵌套循环测试所有参数组合
        for (int k : K_VALUES) {
            for (double lambda : LAMBDA_VALUES) {
                currentConfig++;
                System.out.println(String.format("\n[配置 %d/%d] k=%d, λ=%.1f",
                        currentConfig, totalConfigs, k, lambda));

                List<Double> makespans = new ArrayList<>();

                // 对每个参数组合，运行多个种子和多次实验
                for (long seed : TEST_SEEDS) {
                    for (int run = 1; run <= NUM_RUNS; run++) {
                        double makespan = runSingleTest(k, lambda, seed, run);
                        makespans.add(makespan);
                        System.out.print(".");
                    }
                }
                System.out.println();  // 换行

                // 计算统计指标
                double mean = MetricsCalculator.calculateMean(makespans);
                double std = MetricsCalculator.calculateStd(makespans);
                double min = MetricsCalculator.calculateMin(makespans);
                double max = MetricsCalculator.calculateMax(makespans);

                results.add(new SensitivityResult(k, lambda, mean, std, min, max));

                System.out.println(String.format("  结果: Mean=%.2f, Std=%.2f, Min=%.2f, Max=%.2f",
                        mean, std, min, max));
            }
        }

        // 导出结果
        exportResults(results);

        // 找到最优配置
        findBestConfiguration(results);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("                   ✅ 参数敏感性分析完成！");
        System.out.println("=".repeat(80) + "\n");
    }

    // ==================== 单次测试 ====================

    /**
     * 运行单次参数敏感性测试
     *
     * @param k 动态权重衰减指数
     * @param lambda Bernoulli混沌参数
     * @param seed 随机种子
     * @param run 运行编号
     * @return Makespan
     */
    private static double runSingleTest(int k, double lambda, long seed, int run) {
        CloudSimPlus simulation = new CloudSimPlus();

        // 创建数据中心
        Datacenter datacenter = createDatacenter(simulation);

        // 创建Broker（使用参数化构造函数）
        ICBO_Enhanced_Broker broker = new ICBO_Enhanced_Broker(
                simulation, seed, OMEGA_MAX, OMEGA_MIN, k, lambda);

        // 创建VMs
        List<Vm> vmList = createVms(N);
        broker.submitVmList(vmList);

        // 创建Cloudlets
        List<Cloudlet> cloudletList = createCloudlets(M);
        broker.submitCloudletList(cloudletList);

        // 运行仿真
        simulation.start();

        // 计算Makespan
        double makespan = calculateMakespan(broker.getCloudletFinishedList());

        return makespan;
    }

    // ==================== CloudSim组件创建 ====================

    private static Datacenter createDatacenter(CloudSimPlus simulation) {
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < 40; i++) {  // 40个物理主机
            List<Pe> peList = new ArrayList<>();
            for (int j = 0; j < 4; j++) {  // 每个主机4个处理单元
                peList.add(new PeSimple(2000));  // 2000 MIPS/核心
            }
            Host host = new HostSimple(16384, 1000000, 100000, peList);
            hostList.add(host);
        }
        return new DatacenterSimple(simulation, hostList);
    }

    private static List<Vm> createVms(int N) {
        List<Vm> vmList = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            long mips = 500 + i * 50;  // 异构MIPS: 500-1450
            Vm vm = new VmSimple(mips, 1)
                    .setRam(2048).setBw(10000).setSize(10000);
            vmList.add(vm);
        }
        return vmList;
    }

    private static List<Cloudlet> createCloudlets(int M) {
        List<Cloudlet> cloudletList = new ArrayList<>();
        Random random = new Random(42);  // 固定种子以确保任务集一致
        for (int i = 0; i < M; i++) {
            long length = 10000 + random.nextInt(40000);  // 10K-50K MI
            Cloudlet cloudlet = new CloudletSimple(length, 1);
            cloudletList.add(cloudlet);
        }
        return cloudletList;
    }

    private static double calculateMakespan(List<Cloudlet> finishedCloudlets) {
        return finishedCloudlets.stream()
                .mapToDouble(Cloudlet::getFinishTime)
                .max()
                .orElse(0.0);
    }

    // ==================== 结果处理 ====================

    /**
     * 导出敏感性分析结果到CSV
     */
    private static void exportResults(List<SensitivityResult> results) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String filename = "sensitivity_results_" + timestamp + ".csv";

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // CSV头部
            writer.println("k,lambda,MeanMakespan,StdMakespan,MinMakespan,MaxMakespan,CV");

            // 数据行
            for (SensitivityResult result : results) {
                double cv = result.std / result.mean * 100;  // 变异系数
                writer.println(String.format("%d,%.1f,%.4f,%.4f,%.4f,%.4f,%.2f",
                        result.k, result.lambda, result.mean, result.std,
                        result.min, result.max, cv));
            }

            System.out.println("\n✓ 结果已保存到: " + filename);
        } catch (IOException e) {
            System.err.println("❌ 保存结果失败: " + e.getMessage());
        }
    }

    /**
     * 找到最优参数配置
     */
    private static void findBestConfiguration(List<SensitivityResult> results) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("                     最优参数配置分析");
        System.out.println("=".repeat(80));

        // 按Mean Makespan排序
        results.sort(Comparator.comparingDouble(r -> r.mean));

        System.out.println("\nTop 5 最佳配置（按平均Makespan排序）：");
        System.out.println("Rank | k | λ   | Mean Makespan | Std   | CV%  ");
        System.out.println("-----|---|-----|---------------|-------|------");

        for (int i = 0; i < Math.min(5, results.size()); i++) {
            SensitivityResult r = results.get(i);
            double cv = r.std / r.mean * 100;
            System.out.println(String.format("  %d  | %d | %.1f | %.2f          | %.2f  | %.2f",
                    i + 1, r.k, r.lambda, r.mean, r.std, cv));
        }

        // 检查k=3, λ=0.4的排名
        System.out.println("\n当前默认配置 (k=3, λ=0.4) 的排名：");
        for (int i = 0; i < results.size(); i++) {
            SensitivityResult r = results.get(i);
            if (r.k == 3 && Math.abs(r.lambda - 0.4) < 0.01) {
                System.out.println(String.format("  排名: %d/%d", i + 1, results.size()));
                System.out.println(String.format("  Mean: %.2f", r.mean));
                System.out.println(String.format("  Std: %.2f", r.std));
                break;
            }
        }

        System.out.println("\n✓ 使用plot_sensitivity.py绘制热力图进行可视化分析");
    }

    // ==================== 数据结构 ====================

    /**
     * 敏感性分析结果
     */
    private static class SensitivityResult {
        int k;
        double lambda;
        double mean;
        double std;
        double min;
        double max;

        SensitivityResult(int k, double lambda, double mean, double std, double min, double max) {
            this.k = k;
            this.lambda = lambda;
            this.mean = mean;
            this.std = std;
            this.min = min;
            this.max = max;
        }
    }
}
