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

/**
 * Quick Test - 快速验证批量测试框架
 *
 * 简化配置：
 * - 2个规模：M=50, M=100
 * - 3次运行（而非10次）
 * - 用于快速验证代码正确性
 */
public class QuickTest {

    private static final String[] ALGORITHMS = {"Random", "CBO", "ICBO"};
    private static final int[] TASK_SCALES = {50, 100};  // 简化：只测试2个规模
    private static final int NUM_RUNS = 3;                // 简化：只运行3次
    private static final double VM_TASK_RATIO = 0.2;

    private static Map<String, Map<String, List<Double>>> allResults = new LinkedHashMap<>();

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║   ICBO 快速验证测试                                            ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println("\n配置：" + Arrays.toString(TASK_SCALES) + " 任务，各运行 " + NUM_RUNS + " 次\n");

        // 初始化结果存储
        for (String algorithm : ALGORITHMS) {
            allResults.put(algorithm, new LinkedHashMap<>());
        }

        // 测试每个规模
        for (int taskCount : TASK_SCALES) {
            int vmCount = (int) Math.ceil(taskCount * VM_TASK_RATIO);
            System.out.println("\n========== M=" + taskCount + " 任务，N=" + vmCount + " VM ==========");

            for (String algorithm : ALGORITHMS) {
                System.out.println("\n测试算法：" + algorithm);
                List<Double> makespans = new ArrayList<>();

                for (int run = 1; run <= NUM_RUNS; run++) {
                    System.out.print(String.format("  运行 %d/%d ... ", run, NUM_RUNS));
                    double makespan = runSingleTest(algorithm, taskCount, vmCount);
                    makespans.add(makespan);
                    System.out.println(String.format("Makespan = %.4f", makespan));
                }

                String scaleKey = "M" + taskCount;
                allResults.get(algorithm).put(scaleKey, makespans);

                double mean = MetricsCalculator.calculateMean(makespans);
                double std = MetricsCalculator.calculateStd(makespans);
                System.out.println(String.format("  统计：Mean = %.4f, Std = %.4f", mean, std));
            }
        }

        // 打印对比表格
        ResultWriter.printComparisonTable(allResults);

        // 打印改进率
        printImprovementAnalysis();

        System.out.println("\n✓ 快速验证测试完成！框架运行正常。");
        System.out.println("可以运行 BatchCompareExample 进行完整实验。");
    }

    private static double runSingleTest(String algorithm, int numTasks, int numVms) {
        CloudSimPlus simulation = new CloudSimPlus();
        Datacenter datacenter = createDatacenter(simulation, numVms);
        DatacenterBroker broker = createBroker(simulation, algorithm);

        List<Vm> vmList = createVms(numVms);
        List<Cloudlet> cloudletList = createCloudlets(numTasks);

        broker.submitVmList(vmList);
        broker.submitCloudletList(cloudletList);
        simulation.start();

        return broker.getCloudletFinishedList().stream()
                .mapToDouble(Cloudlet::getFinishTime)
                .max()
                .orElse(0.0);
    }

    private static DatacenterBroker createBroker(CloudSimPlus simulation, String algorithm) {
        DatacenterBroker broker;
        switch (algorithm) {
            case "CBO":
                broker = new CBO_Broker(simulation);
                break;
            case "ICBO":
                broker = new ICBO_Broker(simulation);
                break;
            case "Random":
                broker = new Random_Broker(simulation);
                break;
            default:
                throw new IllegalArgumentException("未知算法：" + algorithm);
        }
        broker.setName(algorithm + "_Broker");
        return broker;
    }

    private static Datacenter createDatacenter(CloudSimPlus simulation, int numVms) {
        int numHosts = Math.max(10, (int) Math.ceil(numVms * 0.5));
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < numHosts; i++) {
            hostList.add(createHost());
        }
        return new DatacenterSimple(simulation, hostList);
    }

    private static Host createHost() {
        List<Pe> peList = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            peList.add(new PeSimple(2000));
        }
        return new HostSimple(16384, 10000, 1000000, peList);
    }

    private static List<Vm> createVms(int numVms) {
        List<Vm> list = new ArrayList<>();
        for (int i = 0; i < numVms; i++) {
            long mips = 500 + (i % 20) * 50;
            Vm vm = new VmSimple(mips, 1);
            vm.setRam(2048).setBw(1000).setSize(10000);
            list.add(vm);
        }
        return list;
    }

    private static List<Cloudlet> createCloudlets(int numTasks) {
        List<Cloudlet> list = new ArrayList<>();
        for (int i = 0; i < numTasks; i++) {
            Cloudlet cloudlet = new CloudletSimple(10000, 1);
            cloudlet.setFileSize(300).setOutputSize(300);
            list.add(cloudlet);
        }
        return list;
    }

    private static void printImprovementAnalysis() {
        System.out.println("\n==================== ICBO改进率（相对于CBO） ====================");
        List<String> scales = new ArrayList<>(allResults.get("CBO").keySet());

        for (String scale : scales) {
            double cboMean = MetricsCalculator.calculateMean(allResults.get("CBO").get(scale));
            double icboMean = MetricsCalculator.calculateMean(allResults.get("ICBO").get(scale));
            double improvement = MetricsCalculator.calculateImprovement(cboMean, icboMean);
            System.out.println(String.format("%s: CBO=%.4f, ICBO=%.4f, 改进率=%+.2f%%",
                    scale, cboMean, icboMean, improvement));
        }
        System.out.println("====================================================================");
    }
}
