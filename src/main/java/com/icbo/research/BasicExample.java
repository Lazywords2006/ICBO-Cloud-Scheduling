package com.icbo.research;

import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
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
 * Basic CloudSim Plus Example
 * 测试CloudSim Plus环境是否正常工作
 */
public class BasicExample {
    private final CloudSimPlus simulation;
    private List<Cloudlet> cloudletList;
    private List<Vm> vmList;

    public static void main(String[] args) {
        new BasicExample();
    }

    private BasicExample() {
        System.out.println("==================== CloudSim Plus 基础示例 ====================");
        System.out.println("初始化CloudSim Plus仿真环境...\n");

        // 1. 初始化仿真引擎
        simulation = new CloudSimPlus();

        // 2. 创建数据中心
        Datacenter datacenter = createDatacenter();
        System.out.println("[OK] 数据中心创建成功: " + datacenter.getHostList().size() + " 个主机");

        // 3. 创建代理
        DatacenterBroker broker = new DatacenterBrokerSimple(simulation);

        // 4. 创建VM和任务
        vmList = createVms();
        cloudletList = createCloudlets();
        System.out.println("[OK] 创建了 " + vmList.size() + " 个虚拟机");
        System.out.println("[OK] 创建了 " + cloudletList.size() + " 个任务\n");

        // 5. 提交VM和任务
        broker.submitVmList(vmList);
        broker.submitCloudletList(cloudletList);

        // 6. 运行仿真
        System.out.println("开始运行仿真...");
        simulation.start();
        System.out.println("[OK] 仿真完成\n");

        // 7. 打印结果
        List<Cloudlet> finishedCloudlets = broker.getCloudletFinishedList();
        System.out.println("==================== 仿真结果 ====================");
        new CloudletsTableBuilder(finishedCloudlets).build();

        // 计算总makespan
        double makespan = finishedCloudlets.stream()
            .mapToDouble(c -> c.getFinishTime())
            .max()
            .orElse(0.0);
        System.out.println("\n总Makespan (完成时间): " + String.format("%.2f", makespan) + " 秒");
        System.out.println("=====================================================");
    }

    /**
     * 创建数据中心
     */
    private Datacenter createDatacenter() {
        // 创建20个物理主机
        List<Host> hostList = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
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
        long mips = 1000; // 每核心处理能力 (MIPS)
        for (int i = 0; i < 4; i++) {
            peList.add(new PeSimple(mips));
        }

        long ram = 8192; // MB
        long storage = 1000000; // MB
        long bw = 10000; // Mbps

        return new HostSimple(ram, bw, storage, peList);
    }

    /**
     * 创建虚拟机列表
     */
    private List<Vm> createVms() {
        List<Vm> list = new ArrayList<>();

        // 创建20个VM（异构）
        for (int i = 0; i < 20; i++) {
            long mips = 500 + i * 50; // 异构速度: 500, 550, 600, ..., 1450 MIPS
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
     */
    private List<Cloudlet> createCloudlets() {
        List<Cloudlet> list = new ArrayList<>();

        // 创建100个任务
        long length = 10000; // MI (Million Instructions)
        long fileSize = 300; // MB
        long outputSize = 300; // MB
        int pesNumber = 1;

        for (int i = 0; i < 100; i++) {
            Cloudlet cloudlet = new CloudletSimple(length, pesNumber);
            cloudlet.setFileSize(fileSize).setOutputSize(outputSize);
            list.add(cloudlet);
        }

        return list;
    }
}
