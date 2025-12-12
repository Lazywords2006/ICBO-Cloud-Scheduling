package com.icbo.research;

import com.icbo.research.utils.ConvergenceRecord;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;

import java.util.*;

/**
 * Random Scheduler Broker for Cloud Task Scheduling
 *
 * 随机调度算法 - 作为性能基准
 *
 * 算法描述：
 * - 将每个任务随机分配到可用的VM上
 * - 不进行任何优化
 * - 作为其他优化算法的性能下限基准
 *
 * 用途：
 * - 验证优化算法是否真正优于随机分配
 * - 提供性能对比的基线
 */
public class Random_Broker extends DatacenterBrokerSimple {

    private final Random random;
    private final long seed;  // ✅ Day 3.1新增：随机种子
    private Map<Long, Vm> cloudletVmMapping;  // cloudletId -> Vm
    private boolean schedulingDone = false;
    private ConvergenceRecord convergenceRecord;  // ✅ Day 3.1新增：收敛记录器

    /**
     * 构造函数
     */
    public Random_Broker(CloudSimPlus simulation) {
        super(simulation);
        this.seed = 42L;  // 固定种子确保可复现性
        this.random = new Random(this.seed);
        this.cloudletVmMapping = new HashMap<>();
    }

    /**
     * 构造函数（带随机种子）
     * @param simulation CloudSim仿真引擎
     * @param seed 随机种子
     */
    public Random_Broker(CloudSimPlus simulation, long seed) {
        super(simulation);
        this.seed = seed;
        this.random = new Random(seed);
        this.cloudletVmMapping = new HashMap<>();
    }

    /**
     * 重写VM映射方法，实现随机调度
     */
    @Override
    protected Vm defaultVmMapper(Cloudlet cloudlet) {
        // 如果还没有进行调度，先运行一次随机分配
        if (!schedulingDone) {
            runRandomScheduling();
            schedulingDone = true;
        }

        // 返回预先计算好的VM映射
        return cloudletVmMapping.getOrDefault(cloudlet.getId(), super.defaultVmMapper(cloudlet));
    }

    /**
     * 运行随机调度算法
     */
    private void runRandomScheduling() {
        List<Cloudlet> cloudletList = new ArrayList<>(getCloudletWaitingList());
        List<Vm> vmList = new ArrayList<>(getVmCreatedList());

        // 如果没有任务或VM，直接返回
        if (cloudletList.isEmpty() || vmList.isEmpty()) {
            return;
        }

        int M = cloudletList.size();  // 任务数
        int N = vmList.size();        // VM数

        // ✅ Day 3.1新增：创建收敛记录器
        String scale = String.format("M%d", M);
        this.convergenceRecord = new ConvergenceRecord("Random", scale, this.seed);

        System.out.println("\n==================== Random调度开始 ====================");
        System.out.println("任务数: " + M);
        System.out.println("VM数: " + N);
        System.out.println("====================================================\n");

        long startTime = System.currentTimeMillis();

        // 随机分配：为每个任务随机选择一个VM
        for (int i = 0; i < M; i++) {
            Cloudlet cloudlet = cloudletList.get(i);
            int vmIndex = random.nextInt(N);  // 随机选择VM索引
            Vm selectedVm = vmList.get(vmIndex);
            cloudletVmMapping.put(cloudlet.getId(), selectedVm);
        }

        // 计算预期Makespan（用于显示）
        double makespan = calculateMakespan(cloudletList, vmList);

        // ✅ Day 3.1新增：记录"收敛曲线"（Random只有1次迭代）
        convergenceRecord.recordIteration(0, makespan);
        convergenceRecord.exportToCSV("results/");

        long endTime = System.currentTimeMillis();

        System.out.println("\n==================== Random调度完成 ====================");
        System.out.println("预期Makespan: " + String.format("%.4f", makespan));
        System.out.println("调度耗时: " + (endTime - startTime) + " ms");
        System.out.println("====================================================");
    }

    /**
     * 计算当前调度方案的Makespan
     */
    private double calculateMakespan(List<Cloudlet> cloudletList, List<Vm> vmList) {
        int N = vmList.size();
        double[] vmLoads = new double[N];

        // 计算每个VM的总负载时间
        for (Cloudlet cloudlet : cloudletList) {
            Vm vm = cloudletVmMapping.get(cloudlet.getId());
            if (vm != null) {
                int vmIndex = vmList.indexOf(vm);
                if (vmIndex >= 0) {
                    double taskLength = cloudlet.getLength();
                    double vmMips = vm.getMips();
                    vmLoads[vmIndex] += taskLength / vmMips;
                }
            }
        }

        // Makespan = 最大负载时间
        return Arrays.stream(vmLoads).max().orElse(0.0);
    }
}
