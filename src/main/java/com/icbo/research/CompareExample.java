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
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * CBO vs ICBO Performance Comparison
 *
 * 对比CBO和ICBO在云任务调度中的性能表现
 *
 * 测试配置：
 * - 任务数 (M): 100
 * - VM数 (N): 20
 * - 种群大小: 30
 * - 迭代次数: 100
 *
 * 预期结果（基于Python实验）：
 * - CBO Makespan: ~33.0671
 * - ICBO Makespan: ~31.8854
 * - ICBO改进率: +3.57%
 */
public class CompareExample {
    private List<Cloudlet> cloudletList;
    private List<Vm> vmList;
    private static final int NUM_TASKS = 100;
    private static final int NUM_VMS = 20;

    public static void main(String[] args) {
        System.out.println("==================== CBO vs ICBO 性能对比 ====================");
        System.out.println("配置: M=" + NUM_TASKS + " 个任务, N=" + NUM_VMS + " 个VM");
        System.out.println("=============================================================\n");

        // 运行CBO测试
        System.out.println("\n========== 测试 1: CBO 算法 ==========");
        double cboMakespan = new CompareExample().runTest(false);

        // 运行ICBO测试
        System.out.println("\n========== 测试 2: ICBO 算法 ==========");
        double icboMakespan = new CompareExample().runTest(true);

        // 对比结果
        System.out.println("\n==================== 性能对比结果 ====================");
        System.out.println(String.format("CBO  Makespan: %.4f", cboMakespan));
        System.out.println(String.format("ICBO Makespan: %.4f", icboMakespan));

        double improvement = ((cboMakespan - icboMakespan) / cboMakespan) * 100;
        System.out.println(String.format("ICBO 改进率: %+.2f%%", improvement));

        if (improvement > 0) {
            System.out.println("\n[结果] ✓ ICBO 性能优于 CBO!");
        } else {
            System.out.println("\n[结果] ✗ ICBO 性能未达预期");
        }

        System.out.println("\n预期改进率（基于Python实验）: +3.57%");
        System.out.println("====================================================");
    }

    /**
     * 运行单次测试
     * @param useICBO true: 使用ICBO, false: 使用CBO
     * @return Makespan（最大完成时间）
     */
    private double runTest(boolean useICBO) {
        // 1. 初始化仿真引擎
        CloudSimPlus simulation = new CloudSimPlus();

        // 2. 创建数据中心
        Datacenter datacenter = createDatacenter(simulation);

        // 3. 创建代理（CBO或ICBO）
        DatacenterBroker broker;
        if (useICBO) {
            broker = new ICBO_Broker(simulation);
            broker.setName("ICBO_Broker");
        } else {
            broker = new CBO_Broker(simulation);
            broker.setName("CBO_Broker");
        }

        // 4. 创建VM和任务
        vmList = createVms();
        cloudletList = createCloudlets();

        // 5. 提交VM和任务
        broker.submitVmList(vmList);
        broker.submitCloudletList(cloudletList);

        // 6. 运行仿真
        simulation.start();

        // 7. 计算Makespan
        List<Cloudlet> finishedCloudlets = broker.getCloudletFinishedList();
        double makespan = finishedCloudlets.stream()
            .mapToDouble(c -> c.getFinishTime())
            .max()
            .orElse(0.0);

        System.out.println("\n[结果] Makespan: " + String.format("%.4f", makespan) + " 秒");

        return makespan;
    }

    /**
     * 创建数据中心
     */
    private Datacenter createDatacenter(CloudSimPlus simulation) {
        // 创建40个物理主机（确保足够容纳20个VM）
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            Host host = createHost();
            hostList.add(host);
        }

        return new DatacenterSimple(simulation, hostList);
    }

    /**
     * 创建单个物理主机
     */
    private Host createHost() {
        // 创建处理器核心
        List<Pe> peList = new ArrayList<>();
        long mips = 2000; // 每核心处理能力 (MIPS) - 增加到2000以容纳所有VM
        for (int i = 0; i < 4; i++) {
            peList.add(new PeSimple(mips));
        }

        long ram = 16384; // MB - 增加RAM以容纳更多VM
        long storage = 1000000; // MB
        long bw = 10000; // Mbps

        return new HostSimple(ram, bw, storage, peList);
    }

    /**
     * 创建虚拟机列表
     * 与Python实验保持一致的异构VM配置
     */
    private List<Vm> createVms() {
        List<Vm> list = new ArrayList<>();

        // 创建20个VM（异构）
        for (int i = 0; i < NUM_VMS; i++) {
            // 异构速度: 500, 550, 600, ..., 1450 MIPS
            long mips = 500 + i * 50;
            long size = 10000; // MB
            int ram = 2048; // MB
            long bw = 1000; // Mbps
            int pesNumber = 1;

            Vm vm = new VmSimple(mips, pesNumber);
            vm.setRam(ram).setBw(bw).setSize(size);
            list.add(vm);
        }

        return list;
    }

    /**
     * 创建任务列表
     * 与Python实验保持一致的任务配置
     */
    private List<Cloudlet> createCloudlets() {
        List<Cloudlet> list = new ArrayList<>();

        // 创建100个任务
        long length = 10000; // MI (Million Instructions)
        long fileSize = 300; // MB
        long outputSize = 300; // MB
        int pesNumber = 1;

        for (int i = 0; i < NUM_TASKS; i++) {
            Cloudlet cloudlet = new CloudletSimple(length, pesNumber);
            cloudlet.setFileSize(fileSize).setOutputSize(outputSize);
            list.add(cloudlet);
        }

        return list;
    }
}
