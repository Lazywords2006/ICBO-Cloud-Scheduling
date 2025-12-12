package com.icbo.research;

import org.cloudsimplus.brokers.DatacenterBroker;
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

import java.util.*;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;
import com.icbo.research.utils.StatisticalTest;

/**
 * Batch Comparison Framework for ICBO Performance Evaluation
 *
 * 批量对比测试框架 - 仿照ERTH论文的实验形式（选项B：混合设计）
 *
 * 实验设计（借鉴ERTH论文，保持ICBO系列对比）：
 * - 任务规模：[50, 100, 200, 300, 500, 1000, 2000] （扩展至大规模）
 * - 对比算法：Random, PSO, GWO, WOA, CBO, ICBO, ICBO-Enhanced（7算法）
 * - 独立运行次数：10次
 * - 资源参数：采用ERTH论文Table 8的参数范围
 *   * VM资源：CPU [100,500], Memory [100,500], Bandwidth [100,250]
 *   * 任务需求：CPU [10,50], Memory [50,100], Bandwidth [10,50]
 * - VM分配：支持固定50 VM（ERTH模式）或动态分配（当前模式）
 * - 评估指标：Makespan（Mean、Std、Min、Max、Rank）
 *
 * 参考论文：
 * - Khatab et al. (2025): CBO算法原理
 * - Qin et al. (2024): ERTH论文的云调度实验设计
 *
 * 输出：
 * - 控制台显示对比表格
 * - CSV文件：原始数据、统计摘要、对比表格
 */
public class BatchCompareExample {

    // 实验配置（多算法对比，借鉴ERTH论文设计）
    // ✅ Day 3.2更新：完整7算法对比（用于Day 3.3收敛曲线实验）
    // 对比算法：Random（基准）、经典群智能算法（PSO、GWO、WOA）、CBO系列（CBO、ICBO、ICBO-Enhanced）
    private static final String[] ALGORITHMS_FULL = {"Random", "PSO", "GWO", "WOA", "CBO", "ICBO", "ICBO-Enhanced"};
    private static final String[] ALGORITHMS_TEST = {"Random", "PSO", "GWO", "WOA", "CBO", "ICBO-Enhanced"};
    private static final String[] ALGORITHMS = ALGORITHMS_FULL;  // 使用完整列表（7算法）

    // 任务规模扩展（仿照ERTH论文：小规模100-1000，大规模1000-2000）
    // ✅ Day 3.2更新：完整7规模（用于Day 3.3收敛曲线实验）
    private static final int[] TASK_SCALES_FULL = {50, 100, 200, 300, 500, 1000, 2000};
    private static final int[] TASK_SCALES_TEST = {100};  // 测试阶段：仅测试1个规模
    private static final int[] TASK_SCALES = TASK_SCALES_FULL;  // ✅ 使用完整7规模
    private static final int NUM_RUNS = 10;                                 // 独立运行次数

    // ⚠️ 随机种子配置（Phase 1: 多随机种子支持）
    // ✅ Day 3.2更新：使用5个种子（用于Day 3.3收敛曲线实验）
    // 测试阶段：2个种子快速验证
    private static final long[] RANDOM_SEEDS_TEST = {42L, 123L};
    // Day 3.3实验：5个种子（7算法 × 7规模 × 5种子 = 245个CSV文件）
    private static final long[] RANDOM_SEEDS_CONVERGENCE = {42L, 123L, 456L, 789L, 1024L};
    // 全量实验：10个种子
    private static final long[] RANDOM_SEEDS_FULL = {
        42L, 123L, 456L, 789L, 1024L,
        2048L, 4096L, 8192L, 16384L, 32768L
    };
    // 当前使用的种子数组（✅ Day 3.3：使用5个种子）
    private static final long[] SEEDS = RANDOM_SEEDS_CONVERGENCE;

    // VM配置（借鉴ERTH：固定50个VM，或保持动态分配）
    private static final boolean USE_FIXED_VMS = false;  // true=固定50 VM, false=动态分配
    private static final int FIXED_VM_COUNT = 50;
    private static final double VM_TASK_RATIO = 0.2;  // VM数 = 任务数 × 0.2（动态模式）

    // 结果存储
    private static Map<String, Map<String, List<Double>>> allResults = new LinkedHashMap<>();

    // ⏱️ Day 2.3新增：时间复杂度数据存储（算法 → 规模 → 运行时间列表，单位：毫秒）
    private static Map<String, Map<String, List<Long>>> timeResults = new LinkedHashMap<>();

    public static void main(String[] args) {
        // ⚠️ 禁用CloudSim Plus详细日志，提升实验速度
        disableCloudSimLogs();

        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║   ICBO 批量性能对比实验（Phase 1: 多随机种子支持）           ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println("\n实验配置：");
        System.out.println("  - 对比算法：" + Arrays.toString(ALGORITHMS));
        System.out.println("  - 任务规模：" + Arrays.toString(TASK_SCALES));
        System.out.println("  - 随机种子数量：" + SEEDS.length + " 个 " + Arrays.toString(SEEDS));
        System.out.println("  - 每个种子运行次数：" + NUM_RUNS + " 次");
        System.out.println("  - 总实验次数：" + (TASK_SCALES.length * ALGORITHMS.length * SEEDS.length * NUM_RUNS) + " 次");
        System.out.println("  - VM分配模式：" + (USE_FIXED_VMS ? "固定" + FIXED_VM_COUNT + " VM（ERTH模式）" : "动态分配（比例" + VM_TASK_RATIO + "）"));
        System.out.println("  - 资源参数：采用ERTH Table 8范围");
        System.out.println("    * VM: CPU[100,500], Mem[100,500], BW[100,250]");
        System.out.println("    * Task: CPU[10,50], Mem[50,100], BW[10,50]");
        System.out.println("\n⚠️ 当前测试模式：使用" + SEEDS.length + "个种子快速验证");
        System.out.println("开始实验...\n");

        long globalStartTime = System.currentTimeMillis();

        // 初始化结果存储
        for (String algorithm : ALGORITHMS) {
            allResults.put(algorithm, new LinkedHashMap<>());
            timeResults.put(algorithm, new LinkedHashMap<>());  // ⏱️ 初始化时间数据存储
        }

        // 对每个任务规模进行测试
        for (int taskCount : TASK_SCALES) {
            // 根据配置选择固定或动态VM分配
            int vmCount = USE_FIXED_VMS ? FIXED_VM_COUNT : (int) Math.ceil(taskCount * VM_TASK_RATIO);
            System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
            System.out.println(String.format("║   测试规模：M=%d 任务，N=%d VM                                ║",
                    taskCount, vmCount));
            System.out.println("╚════════════════════════════════════════════════════════════════╝");

            // 对每个算法运行多次
            for (String algorithm : ALGORITHMS) {
                System.out.println("\n---------- 测试算法：" + algorithm + " ----------");

                List<Double> makespans = new ArrayList<>();
                List<Long> runtimes = new ArrayList<>();  // ⏱️ 存储运行时间（毫秒）

                // ⚠️ Phase 1.3：四层嵌套循环 - 添加种子层
                for (long seed : SEEDS) {
                    System.out.println(String.format("  [Seed %d]", seed));

                    for (int run = 1; run <= NUM_RUNS; run++) {
                        System.out.print(String.format("    运行 %2d/%d ... ", run, NUM_RUNS));

                        // ⏱️ 记录开始时间
                        long startTime = System.currentTimeMillis();

                        // 运行单次测试（传入种子参数）
                        double makespan = runSingleTest(algorithm, taskCount, vmCount, seed, false);

                        // ⏱️ 记录结束时间
                        long endTime = System.currentTimeMillis();
                        long runtime = endTime - startTime;

                        makespans.add(makespan);
                        runtimes.add(runtime);  // ⏱️ 保存运行时间

                        System.out.println(String.format("Makespan = %.4f, Time = %d ms", makespan, runtime));
                    }
                }

                // 存储结果
                String scaleKey = "M" + taskCount;
                allResults.get(algorithm).put(scaleKey, makespans);
                timeResults.get(algorithm).put(scaleKey, runtimes);  // ⏱️ 保存时间数据

                // 打印统计摘要
                MetricsCalculator.Statistics stats = new MetricsCalculator.Statistics(makespans);
                System.out.println("\n" + algorithm + " 统计摘要（跨" + SEEDS.length + "个种子×" + NUM_RUNS + "次运行）：");
                System.out.println(String.format("  Mean = %.4f, Std = %.4f, Min = %.4f, Max = %.4f",
                        stats.mean, stats.std, stats.min, stats.max));

                // ⏱️ 打印时间统计
                long avgTime = (long) runtimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
                long minTime = runtimes.stream().mapToLong(Long::longValue).min().orElse(0L);
                long maxTime = runtimes.stream().mapToLong(Long::longValue).max().orElse(0L);
                double stdTime = calculateStd(runtimes);

                System.out.println(String.format("  运行时间 - Mean = %d ms, Std = %.2f ms, Min = %d ms, Max = %d ms",
                        avgTime, stdTime, minTime, maxTime));
            }
        }

        long globalEndTime = System.currentTimeMillis();
        double totalTime = (globalEndTime - globalStartTime) / 1000.0;

        // 打印最终对比表格
        ResultWriter.printComparisonTable(allResults);

        // 打印改进率分析
        printImprovementAnalysis();

        // ⏱️ Day 2.3新增：打印时间复杂度分析
        printTimeComplexityAnalysis();

        // 导出结果到CSV
        exportResults();

        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║   实验完成！                                                    ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println(String.format("总耗时：%.2f 秒", totalTime));
        System.out.println("结果文件已保存到当前目录");
    }

    /**
     * 运行单次测试
     * @param algorithm 算法名称
     * @param numTasks 任务数
     * @param numVms VM数
     * @param seed 随机种子
     * @param verbose 是否打印详细信息
     * @return Makespan
     */
    private static double runSingleTest(String algorithm, int numTasks, int numVms, long seed, boolean verbose) {
        // 1. 创建仿真引擎
        CloudSimPlus simulation = new CloudSimPlus();

        // 2. 创建数据中心
        Datacenter datacenter = createDatacenter(simulation, numVms);

        // 3. 创建Broker（根据算法类型，传入种子）
        DatacenterBroker broker = createBroker(simulation, algorithm, seed);

        // 4. 创建VM和任务
        List<Vm> vmList = createVms(numVms);
        List<Cloudlet> cloudletList = createCloudlets(numTasks);

        // 5. 提交VM和任务
        broker.submitVmList(vmList);
        broker.submitCloudletList(cloudletList);

        // 6. 运行仿真
        simulation.start();

        // 7. 计算Makespan
        List<Cloudlet> finishedCloudlets = broker.getCloudletFinishedList();
        double makespan = finishedCloudlets.stream()
                .mapToDouble(Cloudlet::getFinishTime)
                .max()
                .orElse(0.0);

        return makespan;
    }

    /**
     * 创建Broker（根据算法类型）
     * @param simulation CloudSim仿真引擎
     * @param algorithm 算法名称
     * @param seed 随机种子
     * @return DatacenterBroker实例
     */
    private static DatacenterBroker createBroker(CloudSimPlus simulation, String algorithm, long seed) {
        DatacenterBroker broker;

        switch (algorithm) {
            case "Random":
                broker = new Random_Broker(simulation, seed);
                broker.setName("Random_Broker");
                break;
            case "PSO":
                broker = new PSO_Broker(simulation, seed);
                broker.setName("PSO_Broker");
                break;
            case "GWO":
                broker = new GWO_Broker(simulation, seed);
                broker.setName("GWO_Broker");
                break;
            case "WOA":
                broker = new WOA_Broker(simulation, seed);
                broker.setName("WOA_Broker");
                break;
            case "CBO":
                broker = new CBO_Broker(simulation, seed);
                broker.setName("CBO_Broker");
                break;
            case "ICBO":
                broker = new ICBO_Broker(simulation, seed);
                broker.setName("ICBO_Broker");
                break;
            case "ICBO-Enhanced":
                broker = new ICBO_Enhanced_Broker(simulation, seed);
                broker.setName("ICBO-Enhanced_Broker");
                break;
            default:
                throw new IllegalArgumentException("未知算法：" + algorithm);
        }

        return broker;
    }

    /**
     * 创建数据中心
     */
    private static Datacenter createDatacenter(CloudSimPlus simulation, int numVms) {
        // 根据VM数量创建足够的物理主机
        int numHosts = (int) Math.ceil(numVms * 0.5);  // 每台主机容纳约2个VM
        if (numHosts < 10) numHosts = 10;  // 至少10台主机

        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < numHosts; i++) {
            Host host = createHost();
            hostList.add(host);
        }

        return new DatacenterSimple(simulation, hostList);
    }

    /**
     * 创建单个物理主机
     */
    private static Host createHost() {
        List<Pe> peList = new ArrayList<>();
        long mips = 2000;  // 每核心2000 MIPS
        for (int i = 0; i < 4; i++) {
            peList.add(new PeSimple(mips));
        }

        long ram = 16384;      // 16GB RAM
        long storage = 1000000; // 1TB storage
        long bw = 10000;       // 10Gbps

        return new HostSimple(ram, bw, storage, peList);
    }

    /**
     * 创建VM列表（异构，采用ERTH论文参数范围）
     * ERTH Table 8: CPU [100, 500], Memory [100, 500], Bandwidth [100, 250]
     */
    private static List<Vm> createVms(int numVms) {
        List<Vm> list = new ArrayList<>();
        Random rand = new Random(42);  // 固定种子保证可复现

        for (int i = 0; i < numVms; i++) {
            // ERTH范围：CPU(MIPS) [100, 500]
            long mips = 100 + rand.nextInt(401);  // [100, 500]

            // ERTH范围：Memory(RAM) [100, 500] MB
            int ram = 100 + rand.nextInt(401);    // [100, 500]

            // ERTH范围：Bandwidth [100, 250] Mbps
            long bw = 100 + rand.nextInt(151);    // [100, 250]

            long size = 10000;  // 10GB 存储
            int pesNumber = 1;

            Vm vm = new VmSimple(mips, pesNumber);
            vm.setRam(ram).setBw(bw).setSize(size);
            list.add(vm);
        }

        return list;
    }

    /**
     * 创建任务列表（采用ERTH论文参数范围）
     * ERTH Table 8: CPU [10, 50], Memory [50, 100], Bandwidth [10, 50]
     */
    private static List<Cloudlet> createCloudlets(int numTasks) {
        List<Cloudlet> list = new ArrayList<>();
        Random rand = new Random(42);  // 固定种子保证可复现

        for (int i = 0; i < numTasks; i++) {
            // ERTH范围：CPU需求(Length) [10, 50] × 1000 MI
            long length = (10 + rand.nextInt(41)) * 1000;  // [10000, 50000] MI

            // ERTH范围：Memory需求(FileSize) [50, 100] MB
            long fileSize = 50 + rand.nextInt(51);         // [50, 100] MB

            // ERTH范围：Bandwidth需求(OutputSize) [10, 50] MB
            long outputSize = 10 + rand.nextInt(41);       // [10, 50] MB

            int pesNumber = 1;

            Cloudlet cloudlet = new CloudletSimple(length, pesNumber);
            cloudlet.setFileSize(fileSize).setOutputSize(outputSize);
            list.add(cloudlet);
        }

        return list;
    }

    /**
     * 打印改进率分析
     */
    private static void printImprovementAnalysis() {
        System.out.println("\n==================== 改进率分析（相对于CBO） ====================");

        List<String> scales = new ArrayList<>(allResults.get("CBO").keySet());

        System.out.println(String.format("%-10s | %-15s | %-18s | %-18s",
                "规模", "CBO Mean", "ICBO-E Mean", "ICBO-E改进率"));
        System.out.println("-".repeat(75));

        double totalImprovement = 0.0;
        int count = 0;

        for (String scale : scales) {
            double cboMean = MetricsCalculator.calculateMean(allResults.get("CBO").get(scale));
            double icboEMean = MetricsCalculator.calculateMean(allResults.get("ICBO-Enhanced").get(scale));
            double improvement = MetricsCalculator.calculateImprovement(cboMean, icboEMean);

            System.out.println(String.format("%-10s | %15.4f | %18.4f | %+17.2f%%",
                    scale, cboMean, icboEMean, improvement));

            totalImprovement += improvement;
            count++;
        }

        double avgImprovement = totalImprovement / count;
        System.out.println("-".repeat(75));
        System.out.println(String.format("平均改进率：%+.2f%%", avgImprovement));
        System.out.println("====================================================================\n");
    }

    /**
     * 导出结果到CSV文件
     */
    private static void exportResults() {
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());

        // 导出原始数据
        ResultWriter.exportRawData("ICBO_RawData_" + timestamp + ".csv", allResults);

        // 导出统计摘要
        ResultWriter.exportStatisticsSummary("ICBO_Statistics_" + timestamp + ".csv", allResults);

        // 导出对比表格
        ResultWriter.exportComparisonTable("ICBO_Comparison_" + timestamp + ".csv", allResults);

        // ✅ Phase 2新增：导出统计检验结果（解决Peer Review Critical问题）
        if (allResults.containsKey("CBO") && allResults.containsKey("ICBO-Enhanced")) {
            ResultWriter.exportStatisticalComparison(
                    "ICBO_StatisticalTests_" + timestamp + ".csv",
                    allResults,
                    "CBO",
                    "ICBO-Enhanced"
            );

            // 打印统计检验摘要到控制台
            ResultWriter.printStatisticalSummary(allResults, "CBO", "ICBO-Enhanced");
        }

        // ⭐ Day 1.2新增：Friedman检验（多算法对比）
        performFriedmanTest(timestamp);

        // ⏱️ Day 2.3新增：导出时间复杂度报告
        exportTimeComplexityReport(timestamp);
    }

    /**
     * ⭐ 执行Friedman检验（多算法非参数检验）
     *
     * Friedman检验用于比较多个算法在多个数据集（规模）上的性能差异
     * 是同行评审中必须的统计检验方法
     *
     * @param timestamp 时间戳（用于文件名）
     */
    private static void performFriedmanTest(String timestamp) {
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║   Friedman检验：多算法性能对比（Critical Peer Review）        ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");

        // 获取所有规模
        List<String> scales = new ArrayList<>(allResults.get(ALGORITHMS[0]).keySet());
        int k = ALGORITHMS.length;  // 算法数
        int N = scales.size();       // 数据集数（规模数）

        System.out.println(String.format("\n实验配置："));
        System.out.println(String.format("  - 算法数 k = %d", k));
        System.out.println(String.format("  - 数据集数 N = %d （规模：%s）", N, scales));
        System.out.println(String.format("  - 显著性水平 α = 0.05"));

        // 构建数据矩阵：data[i][j] = 算法i在规模j上的平均性能
        double[][] data = new double[k][N];
        for (int i = 0; i < k; i++) {
            String algorithm = ALGORITHMS[i];
            for (int j = 0; j < N; j++) {
                String scale = scales.get(j);
                List<Double> results = allResults.get(algorithm).get(scale);
                data[i][j] = MetricsCalculator.calculateMean(results);
            }
        }

        // 执行Friedman检验
        StatisticalTest.FriedmanTestResult result = StatisticalTest.friedmanTestFull(data, 0.05);

        // 打印结果
        System.out.println(String.format("\n✅ Friedman检验结果："));
        System.out.println(String.format("  - χ² 统计量 = %.4f", result.chiSquare));
        System.out.println(String.format("  - p-value = %.4e %s", result.pValue,
                StatisticalTest.interpretPValue(result.pValue)));
        System.out.println(String.format("  - 结论：%s （p < 0.05）",
                result.isSignificant ? "算法间存在显著差异 ⭐" : "算法间无显著差异"));

        System.out.println(String.format("\n✅ 平均排名（越小越好）："));
        System.out.println(String.format("%-20s | %10s | %10s", "算法", "平均排名", "排名等级"));
        System.out.println("-".repeat(45));

        // 按平均排名排序并显示
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            indices.add(i);
        }
        indices.sort((a, b) -> Double.compare(result.averageRanks[a], result.averageRanks[b]));

        for (int rank = 0; rank < k; rank++) {
            int idx = indices.get(rank);
            String algorithm = ALGORITHMS[idx];
            double avgRank = result.averageRanks[idx];

            String medal = "";
            if (rank == 0) medal = "🥇";
            else if (rank == 1) medal = "🥈";
            else if (rank == 2) medal = "🥉";

            System.out.println(String.format("%-20s | %10.2f | %-10s %s",
                    algorithm, avgRank, (rank + 1) + "位", medal));
        }

        System.out.println("\n✅ Nemenyi后续检验（临界差值CD）：");
        System.out.println(String.format("  - CD = %.4f （α = 0.05）", result.criticalDifference));
        System.out.println(String.format("  - 如果两个算法的平均排名差 > %.4f，则显著不同", result.criticalDifference));

        // 进行两两比较
        System.out.println(String.format("\n✅ 两两比较（Nemenyi检验）："));
        System.out.println(String.format("%-20s vs %-20s | %10s | %s",
                "算法1", "算法2", "排名差", "结论"));
        System.out.println("-".repeat(75));

        for (int i = 0; i < k; i++) {
            for (int j = i + 1; j < k; j++) {
                double rankDiff = Math.abs(result.averageRanks[i] - result.averageRanks[j]);
                boolean significant = rankDiff > result.criticalDifference;
                String conclusion = significant ? "显著不同 ⭐" : "无显著差异";

                System.out.println(String.format("%-20s vs %-20s | %10.4f | %s",
                        ALGORITHMS[i], ALGORITHMS[j], rankDiff, conclusion));
            }
        }

        // 导出Friedman检验结果到CSV
        exportFriedmanResultToCSV(timestamp, result, scales);

        System.out.println(String.format("\n✅ Friedman检验结果已保存到：results/ICBO_FriedmanTest_%s.csv", timestamp));
        System.out.println("══════════════════════════════════════════════════════════════════\n");
    }

    /**
     * 导出Friedman检验结果到CSV文件
     */
    private static void exportFriedmanResultToCSV(String timestamp, StatisticalTest.FriedmanTestResult result, List<String> scales) {
        try (java.io.PrintWriter writer = new java.io.PrintWriter("results/ICBO_FriedmanTest_" + timestamp + ".csv")) {
            // 写入检验摘要
            writer.println("Friedman Test Summary");
            writer.println("Test Statistic (Chi-Square)," + result.chiSquare);
            writer.println("p-value," + result.pValue);
            writer.println("Significant (p<0.05)," + (result.isSignificant ? "Yes" : "No"));
            writer.println("Critical Difference (CD)," + result.criticalDifference);
            writer.println("Number of Algorithms," + ALGORITHMS.length);
            writer.println("Number of Datasets," + scales.size());
            writer.println();

            // 写入平均排名
            writer.println("Average Ranks");
            writer.println("Algorithm,Average Rank,Rank Position");
            for (int i = 0; i < ALGORITHMS.length; i++) {
                writer.println(String.format("%s,%.4f,%d",
                        ALGORITHMS[i], result.averageRanks[i],
                        getRankPosition(result.averageRanks, result.averageRanks[i])));
            }
            writer.println();

            // 写入两两比较
            writer.println("Pairwise Comparisons (Nemenyi Post-hoc Test)");
            writer.println("Algorithm 1,Algorithm 2,Rank Difference,Significant (>CD)");
            for (int i = 0; i < ALGORITHMS.length; i++) {
                for (int j = i + 1; j < ALGORITHMS.length; j++) {
                    double rankDiff = Math.abs(result.averageRanks[i] - result.averageRanks[j]);
                    boolean significant = rankDiff > result.criticalDifference;
                    writer.println(String.format("%s,%s,%.4f,%s",
                            ALGORITHMS[i], ALGORITHMS[j], rankDiff, (significant ? "Yes" : "No")));
                }
            }

        } catch (Exception e) {
            System.err.println("✗ 导出Friedman检验结果失败: " + e.getMessage());
        }
    }

    /**
     * 计算某个排名值在所有排名中的位置（从1开始）
     */
    private static int getRankPosition(double[] ranks, double targetRank) {
        int position = 1;
        for (double rank : ranks) {
            if (rank < targetRank) {
                position++;
            }
        }
        return position;
    }

    /**
     * ⏱️ Day 2.3新增：计算时间列表的标准差
     *
     * @param times 时间列表（毫秒）
     * @return 标准差（毫秒）
     */
    private static double calculateStd(List<Long> times) {
        if (times.isEmpty()) {
            return 0.0;
        }

        double mean = times.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double variance = times.stream()
                .mapToDouble(t -> Math.pow(t - mean, 2))
                .average()
                .orElse(0.0);

        return Math.sqrt(variance);
    }

    /**
     * ⏱️ Day 2.3新增：打印时间复杂度分析
     *
     * 展示每个算法在不同规模下的运行时间统计
     * 计算时间复杂度增长率（相对于基准规模）
     */
    private static void printTimeComplexityAnalysis() {
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║   时间复杂度分析（Time Complexity Analysis）                  ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");

        List<String> scales = new ArrayList<>(timeResults.get(ALGORITHMS[0]).keySet());

        // 打印表头
        System.out.println(String.format("\n%-15s | %-12s | %-12s | %-12s | %-15s",
                "算法", "规模", "平均时间(ms)", "标准差(ms)", "相对基准增长率"));
        System.out.println("-".repeat(85));

        for (String algorithm : ALGORITHMS) {
            Map<String, List<Long>> scaleTimeMap = timeResults.get(algorithm);

            // 获取基准规模（第一个规模）的平均时间
            String baseScale = scales.get(0);
            List<Long> baseTimes = scaleTimeMap.get(baseScale);
            double baseAvg = baseTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);

            boolean firstRow = true;
            for (String scale : scales) {
                List<Long> times = scaleTimeMap.get(scale);
                double avgTime = times.stream().mapToLong(Long::longValue).average().orElse(0.0);
                double stdTime = calculateStd(times);

                // 计算相对基准规模的增长率
                double growthRate = ((avgTime - baseAvg) / baseAvg) * 100.0;
                String growthStr = scale.equals(baseScale) ? "基准" : String.format("%+.2f%%", growthRate);

                String algoName = firstRow ? algorithm : "";
                System.out.println(String.format("%-15s | %-12s | %12.2f | %12.2f | %15s",
                        algoName, scale, avgTime, stdTime, growthStr));

                firstRow = false;
            }

            if (!algorithm.equals(ALGORITHMS[ALGORITHMS.length - 1])) {
                System.out.println("-".repeat(85));
            }
        }

        System.out.println("═".repeat(85));

        // 计算并显示时间复杂度等级（基于最大规模 vs 最小规模的时间比例）
        System.out.println("\n✅ 时间复杂度等级估算：");
        System.out.println(String.format("%-15s | %-20s | %-20s", "算法", "最小→最大时间倍数", "估算复杂度等级"));
        System.out.println("-".repeat(65));

        String minScale = scales.get(0);
        String maxScale = scales.get(scales.size() - 1);

        for (String algorithm : ALGORITHMS) {
            List<Long> minTimes = timeResults.get(algorithm).get(minScale);
            List<Long> maxTimes = timeResults.get(algorithm).get(maxScale);

            double minAvg = minTimes.stream().mapToLong(Long::longValue).average().orElse(1.0);
            double maxAvg = maxTimes.stream().mapToLong(Long::longValue).average().orElse(1.0);

            double ratio = maxAvg / minAvg;
            String complexity = estimateComplexity(ratio, TASK_SCALES);

            System.out.println(String.format("%-15s | %20.2fx | %-20s",
                    algorithm, ratio, complexity));
        }

        System.out.println("═".repeat(65));
        System.out.println();
    }

    /**
     * ⏱️ 估算时间复杂度等级
     *
     * 根据最大规模和最小规模的时间比例,估算算法的时间复杂度
     *
     * @param ratio 最大规模时间 / 最小规模时间
     * @param scales 规模数组
     * @return 复杂度等级字符串
     */
    private static String estimateComplexity(double ratio, int[] scales) {
        int minScale = scales[0];
        int maxScale = scales[scales.length - 1];
        double scaleRatio = (double) maxScale / minScale;

        // 理论倍数：如果是O(n)，时间应该增长scaleRatio倍
        // 如果是O(n²)，时间应该增长scaleRatio²倍
        double linearExpected = scaleRatio;
        double quadraticExpected = Math.pow(scaleRatio, 2);

        // 根据实际倍数与理论倍数的接近程度判断
        if (ratio < linearExpected * 1.5) {
            return "O(n) ~ 线性";
        } else if (ratio < quadraticExpected * 0.5) {
            return "O(n log n) ~ 准线性";
        } else if (ratio < quadraticExpected * 1.5) {
            return "O(n²) ~ 二次";
        } else {
            return "O(n³) 或更高";
        }
    }

    /**
     * ⏱️ Day 2.3新增：导出时间复杂度报告到CSV
     *
     * 生成包含以下内容的CSV文件：
     * 1. 每个算法在每个规模下的时间统计（平均、标准差、最小、最大）
     * 2. 时间增长率分析
     * 3. 时间复杂度等级估算
     *
     * @param timestamp 时间戳（用于文件名）
     */
    private static void exportTimeComplexityReport(String timestamp) {
        String filename = "results/ICBO_TimeComplexity_" + timestamp + ".csv";

        try (java.io.PrintWriter writer = new java.io.PrintWriter(filename)) {
            // 写入标题
            writer.println("Time Complexity Analysis Report");
            writer.println("Generated at," + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            writer.println("Number of Algorithms," + ALGORITHMS.length);
            writer.println("Number of Scales," + TASK_SCALES.length);
            writer.println("Runs per Scale," + (SEEDS.length * NUM_RUNS));
            writer.println();

            // 写入详细时间统计
            writer.println("Detailed Time Statistics");
            writer.println("Algorithm,Scale,Mean Time (ms),Std Time (ms),Min Time (ms),Max Time (ms),Total Time (ms)");

            List<String> scales = new ArrayList<>(timeResults.get(ALGORITHMS[0]).keySet());

            for (String algorithm : ALGORITHMS) {
                for (String scale : scales) {
                    List<Long> times = timeResults.get(algorithm).get(scale);
                    double avgTime = times.stream().mapToLong(Long::longValue).average().orElse(0.0);
                    double stdTime = calculateStd(times);
                    long minTime = times.stream().mapToLong(Long::longValue).min().orElse(0L);
                    long maxTime = times.stream().mapToLong(Long::longValue).max().orElse(0L);
                    long totalTime = times.stream().mapToLong(Long::longValue).sum();

                    writer.println(String.format("%s,%s,%.2f,%.2f,%d,%d,%d",
                            algorithm, scale, avgTime, stdTime, minTime, maxTime, totalTime));
                }
            }
            writer.println();

            // 写入时间增长率分析
            writer.println("Time Growth Rate Analysis (Relative to Baseline Scale)");
            writer.println("Algorithm,Scale,Mean Time (ms),Growth Rate (%)");

            for (String algorithm : ALGORITHMS) {
                Map<String, List<Long>> scaleTimeMap = timeResults.get(algorithm);

                // 获取基准规模（第一个规模）的平均时间
                String baseScale = scales.get(0);
                List<Long> baseTimes = scaleTimeMap.get(baseScale);
                double baseAvg = baseTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);

                for (String scale : scales) {
                    List<Long> times = scaleTimeMap.get(scale);
                    double avgTime = times.stream().mapToLong(Long::longValue).average().orElse(0.0);

                    // 计算相对基准规模的增长率
                    double growthRate = ((avgTime - baseAvg) / baseAvg) * 100.0;
                    String growthStr = scale.equals(baseScale) ? "Baseline" : String.format("%.2f", growthRate);

                    writer.println(String.format("%s,%s,%.2f,%s",
                            algorithm, scale, avgTime, growthStr));
                }
            }
            writer.println();

            // 写入时间复杂度等级估算
            writer.println("Time Complexity Class Estimation");
            writer.println("Algorithm,Min Scale,Max Scale,Min Avg Time (ms),Max Avg Time (ms),Time Ratio,Estimated Complexity");

            String minScale = scales.get(0);
            String maxScale = scales.get(scales.size() - 1);

            for (String algorithm : ALGORITHMS) {
                List<Long> minTimes = timeResults.get(algorithm).get(minScale);
                List<Long> maxTimes = timeResults.get(algorithm).get(maxScale);

                double minAvg = minTimes.stream().mapToLong(Long::longValue).average().orElse(1.0);
                double maxAvg = maxTimes.stream().mapToLong(Long::longValue).average().orElse(1.0);

                double ratio = maxAvg / minAvg;
                String complexity = estimateComplexity(ratio, TASK_SCALES);

                writer.println(String.format("%s,%s,%s,%.2f,%.2f,%.2f,%s",
                        algorithm, minScale, maxScale, minAvg, maxAvg, ratio, complexity));
            }

            System.out.println(String.format("\n✅ 时间复杂度报告已保存到：%s", filename));

        } catch (Exception e) {
            System.err.println("✗ 导出时间复杂度报告失败: " + e.getMessage());
        }
    }

    /**
     * 禁用CloudSim Plus详细日志
     *
     * CloudSim默认输出大量INFO级别日志（VM创建、Cloudlet提交等），
     * 导致实验输出冗长且运行缓慢。此方法禁用CloudSim框架日志，
     * 仅保留算法迭代输出（由Broker类输出）。
     *
     * 性能提升：60次实验从10分钟减少到2-3分钟
     */
    private static void disableCloudSimLogs() {
        // 禁用CloudSim Plus根logger
        Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.OFF);

        // 禁用CloudSim Plus所有组件的日志
        ((Logger) LoggerFactory.getLogger("org.cloudsimplus")).setLevel(Level.OFF);

        // 如果需要调试，可以只禁用特定组件：
        // ((Logger) LoggerFactory.getLogger("org.cloudsimplus.brokers")).setLevel(Level.OFF);
        // ((Logger) LoggerFactory.getLogger("org.cloudsimplus.datacenters")).setLevel(Level.OFF);
        // ((Logger) LoggerFactory.getLogger("org.cloudsimplus.vms")).setLevel(Level.OFF);
    }
}
