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
    // 对比算法：Random（基准）、经典群智能算法（GWO、WOA）、CBO系列（CBO、ICBO-Enhanced）
    // ⚠️ 用户要求：移除ICBO和PSO，只保留ICBO-Enhanced
    private static final String[] ALGORITHMS_FULL = {"Random", "GWO", "WOA", "CBO", "ICBO-Enhanced"};
    private static final String[] ALGORITHMS_TEST = {"Random", "GWO", "WOA", "CBO", "ICBO-Enhanced"};
    private static final String[] ALGORITHMS = ALGORITHMS_FULL;  // 使用完整列表

    // 任务规模扩展（仿照ERTH论文：小规模100-1000，大规模1000-2000）
    // 注：M=5000计算成本过高，暂时移除以保证实验可行性
    private static final int[] TASK_SCALES_FULL = {50, 100, 200, 300, 500, 1000, 2000};
    private static final int[] TASK_SCALES_TEST = {100};  // 测试阶段：仅测试1个规模
    private static final int[] TASK_SCALES = TASK_SCALES_TEST;  // ⚠️ 测试模式
    private static final int NUM_RUNS = 10;                                 // 独立运行次数

    // ⚠️ 随机种子配置（Phase 1: 多随机种子支持）
    // 测试阶段：2个种子快速验证
    private static final long[] RANDOM_SEEDS_TEST = {42L, 123L};
    // 全量实验：10个种子
    private static final long[] RANDOM_SEEDS = {
        42L, 123L, 456L, 789L, 1024L,
        2048L, 4096L, 8192L, 16384L, 32768L
    };
    // 当前使用的种子数组（✅ 切换到全量实验）
    private static final long[] SEEDS = RANDOM_SEEDS;

    // VM配置（借鉴ERTH：固定50个VM，或保持动态分配）
    private static final boolean USE_FIXED_VMS = false;  // true=固定50 VM, false=动态分配
    private static final int FIXED_VM_COUNT = 50;
    private static final double VM_TASK_RATIO = 0.2;  // VM数 = 任务数 × 0.2（动态模式）

    // 结果存储
    private static Map<String, Map<String, List<Double>>> allResults = new LinkedHashMap<>();

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

                // ⚠️ Phase 1.3：四层嵌套循环 - 添加种子层
                for (long seed : SEEDS) {
                    System.out.println(String.format("  [Seed %d]", seed));

                    for (int run = 1; run <= NUM_RUNS; run++) {
                        System.out.print(String.format("    运行 %2d/%d ... ", run, NUM_RUNS));

                        // 运行单次测试（传入种子参数）
                        double makespan = runSingleTest(algorithm, taskCount, vmCount, seed, false);
                        makespans.add(makespan);

                        System.out.println(String.format("Makespan = %.4f", makespan));
                    }
                }

                // 存储结果
                String scaleKey = "M" + taskCount;
                allResults.get(algorithm).put(scaleKey, makespans);

                // 打印统计摘要
                MetricsCalculator.Statistics stats = new MetricsCalculator.Statistics(makespans);
                System.out.println("\n" + algorithm + " 统计摘要（跨" + SEEDS.length + "个种子×" + NUM_RUNS + "次运行）：");
                System.out.println(String.format("  Mean = %.4f, Std = %.4f, Min = %.4f, Max = %.4f",
                        stats.mean, stats.std, stats.min, stats.max));
            }
        }

        long globalEndTime = System.currentTimeMillis();
        double totalTime = (globalEndTime - globalStartTime) / 1000.0;

        // 打印最终对比表格
        ResultWriter.printComparisonTable(allResults);

        // 打印改进率分析
        printImprovementAnalysis();

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
