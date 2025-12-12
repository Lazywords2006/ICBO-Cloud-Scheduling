package com.icbo.research;

import com.icbo.research.utils.ConvergenceRecord;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;

import java.util.*;

/**
 * WOA_Broker - Whale Optimization Algorithm for Cloud Task Scheduling
 *
 * 鲸鱼优化算法
 *
 * 算法特点：
 * - 模拟座头鲸的狩猎行为（气泡网捕食法）
 * - 三种行为：包围猎物、螺旋气泡网攻击、搜索猎物
 * - 探索与开发的自适应平衡
 *
 * 参数配置（来自CBO论文）：
 * - Contraction coefficient (A) = 2a*r - a（a从2线性衰减到0）
 * - Encircling Probability (P-enc) = 0.5
 * - Spiral constant b = 1
 * - Population Size = 30
 * - Max Iterations = 100
 *
 * 经典参考：
 * - Mirjalili & Lewis (2016): "The Whale Optimization Algorithm"
 * - Khatab et al. (2025): CBO论文使用WOA作为对比算法
 */
public class WOA_Broker extends DatacenterBrokerSimple {

    // WOA参数
    protected static final int POPULATION_SIZE = 30;       // 鲸鱼数量
    protected static final int MAX_ITERATIONS = 100;       // 最大迭代次数
    protected static final double B = 1.0;                 // 螺旋形状常数

    protected final Random random;
    protected final long seed;
    private ConvergenceRecord convergenceRecord;  // ✅ Day 3.1新增：收敛记录器

    /**
     * 构造函数（带随机种子）
     * @param simulation CloudSim仿真引擎
     * @param seed 随机种子
     */
    public WOA_Broker(CloudSimPlus simulation, long seed) {
        super(simulation);
        this.seed = seed;
        this.random = new Random(seed);
    }

    /**
     * 构造函数（向后兼容，使用默认种子42）
     */
    public WOA_Broker(CloudSimPlus simulation) {
        this(simulation, 42L);
    }

    @Override
    protected void requestDatacentersToCreateWaitingCloudlets() {
        if (!getCloudletWaitingList().isEmpty()) {
            List<Cloudlet> cloudletList = new ArrayList<>(getCloudletWaitingList());
            List<Vm> vmList = getVmCreatedList();

            int M = cloudletList.size();  // 任务数
            int N = vmList.size();        // VM数

            System.out.println("\n==================== WOA算法开始 ===================");
            System.out.println("任务数: " + M);
            System.out.println("VM数: " + N);
            System.out.println("算法参数: PopSize=" + POPULATION_SIZE + ", MaxIter=" + MAX_ITERATIONS +
                    ", a=2→0, b=" + B);
            System.out.println("====================================================\n");

            long startTime = System.currentTimeMillis();

            // WOA优化
            int[] bestSchedule = woaOptimize(cloudletList, vmList, M, N);

            // 应用最优调度方案
            for (int i = 0; i < M; i++) {
                Cloudlet cloudlet = cloudletList.get(i);
                Vm vm = vmList.get(bestSchedule[i]);
                cloudlet.setVm(vm);
            }

            // 计算最优Makespan
            double makespan = calculateMakespan(bestSchedule, cloudletList, vmList, M, N);

            long endTime = System.currentTimeMillis();

            System.out.println("==================== WOA算法完成 ===================");
            System.out.println("最优Makespan: " + String.format("%.4f", makespan));
            System.out.println("算法运行时间: " + (endTime - startTime) + " ms");
            System.out.println("====================================================\n");
        }

        super.requestDatacentersToCreateWaitingCloudlets();
    }

    /**
     * WOA优化主流程
     */
    protected int[] woaOptimize(List<Cloudlet> cloudletList, List<Vm> vmList, int M, int N) {
        // 初始化鲸鱼群
        double[][] whales = new double[POPULATION_SIZE][M];  // 鲸鱼位置（连续空间[0,1]）
        double[] fitness = new double[POPULATION_SIZE];      // 适应度

        // ✅ Day 3.1新增：创建收敛记录器
        String scale = String.format("M%d", M);
        this.convergenceRecord = new ConvergenceRecord("WOA", scale, this.seed);

        // 最优鲸鱼（猎物位置）
        double[] bestPos = new double[M];
        double bestScore = Double.MAX_VALUE;

        // 初始化鲸鱼位置
        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int j = 0; j < M; j++) {
                whales[i][j] = random.nextDouble();  // 位置 ∈ [0, 1]
            }
            fitness[i] = calculateFitness(whales[i], M, N, cloudletList, vmList);

            // 更新最优位置
            if (fitness[i] < bestScore) {
                bestScore = fitness[i];
                System.arraycopy(whales[i], 0, bestPos, 0, M);
            }
        }

        // WOA迭代
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            // 计算收敛参数a（从2线性减少到0）
            double a = 2.0 - iter * (2.0 / MAX_ITERATIONS);

            // 更新每只鲸鱼的位置
            for (int i = 0; i < POPULATION_SIZE; i++) {
                double r = random.nextDouble();      // 随机数 [0, 1]
                double p = random.nextDouble();      // 随机数 [0, 1]
                double l = (random.nextDouble() - 0.5) * 2;  // 随机数 [-1, 1]

                for (int j = 0; j < M; j++) {
                    if (p < 0.5) {
                        // 包围猎物或搜索猎物（收缩包围机制）
                        double A = 2 * a * r - a;
                        double C = 2 * r;

                        if (Math.abs(A) < 1) {
                            // 包围猎物（开发）
                            double D = Math.abs(C * bestPos[j] - whales[i][j]);
                            whales[i][j] = bestPos[j] - A * D;
                        } else {
                            // 搜索猎物（探索）
                            int randomIdx = random.nextInt(POPULATION_SIZE);
                            double D = Math.abs(C * whales[randomIdx][j] - whales[i][j]);
                            whales[i][j] = whales[randomIdx][j] - A * D;
                        }
                    } else {
                        // 螺旋气泡网攻击（开发）
                        double D = Math.abs(bestPos[j] - whales[i][j]);
                        whales[i][j] = D * Math.exp(B * l) * Math.cos(2 * Math.PI * l) + bestPos[j];
                    }

                    // 位置边界处理
                    if (whales[i][j] > 1.0) whales[i][j] = 1.0;
                    if (whales[i][j] < 0.0) whales[i][j] = 0.0;
                }

                // 计算适应度
                fitness[i] = calculateFitness(whales[i], M, N, cloudletList, vmList);

                // 更新最优位置
                if (fitness[i] < bestScore) {
                    bestScore = fitness[i];
                    System.arraycopy(whales[i], 0, bestPos, 0, M);
                }
            }

            // 每20次迭代打印一次进度
            if ((iter + 1) % 20 == 0 || iter == 0) {
                System.out.println(String.format("Iter %3d/%d: Best Makespan = %.4f, a = %.4f",
                        iter + 1, MAX_ITERATIONS, bestScore, a));
            }

            // ✅ Day 3.1新增：记录收敛曲线
            convergenceRecord.recordIteration(iter, bestScore);
        }

        // ✅ Day 3.1新增：导出收敛曲线到CSV
        convergenceRecord.exportToCSV("results/");

        // 将最优鲸鱼的连续解转换为离散调度方案
        return continuousToDiscrete(bestPos, N);
    }

    /**
     * 计算适应度（Makespan）
     */
    protected double calculateFitness(double[] individual, int M, int N,
                                      List<Cloudlet> cloudletList, List<Vm> vmList) {
        int[] schedule = continuousToDiscrete(individual, N);
        return calculateMakespan(schedule, cloudletList, vmList, M, N);
    }

    /**
     * 连续解 → 离散解
     */
    protected int[] continuousToDiscrete(double[] continuous, int N) {
        int[] discrete = new int[continuous.length];
        for (int i = 0; i < continuous.length; i++) {
            discrete[i] = (int) (continuous[i] * N);
            if (discrete[i] >= N) {
                discrete[i] = N - 1;
            }
        }
        return discrete;
    }

    /**
     * 计算Makespan
     */
    protected double calculateMakespan(int[] schedule, List<Cloudlet> cloudletList, List<Vm> vmList, int M, int N) {
        double[] vmLoads = new double[N];

        for (int i = 0; i < M; i++) {
            int vmIdx = schedule[i];
            double taskLength = cloudletList.get(i).getLength();
            double vmMips = vmList.get(vmIdx).getMips();
            vmLoads[vmIdx] += taskLength / vmMips;
        }

        return Arrays.stream(vmLoads).max().orElse(0.0);
    }
}
