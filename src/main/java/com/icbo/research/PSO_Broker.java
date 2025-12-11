package com.icbo.research;

import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;

import java.util.*;

/**
 * PSO_Broker - Particle Swarm Optimization for Cloud Task Scheduling
 *
 * PSO粒子群优化算法
 *
 * 算法特点：
 * - 群体智能：模拟鸟群觅食行为
 * - 双向学习：个体最优（pBest）+ 全局最优（gBest）
 * - 速度-位置更新：惯性 + 认知 + 社会学习
 *
 * 参数配置（来自CBO论文）：
 * - Cognitive Learning Rate (C1) = 1.5
 * - Social Learning Rate (C2) = 1.5
 * - Inertia Weight (w) = 0.9 → 0.4（线性衰减）
 * - Population Size = 30
 * - Max Iterations = 100
 *
 * 经典参考：
 * - Kennedy & Eberhart (1995): "Particle Swarm Optimization"
 * - Khatab et al. (2025): CBO论文使用PSO作为对比算法
 */
public class PSO_Broker extends DatacenterBrokerSimple {

    // PSO参数（来自CBO论文）
    protected static final int POPULATION_SIZE = 30;       // 粒子数量
    protected static final int MAX_ITERATIONS = 100;       // 最大迭代次数
    protected static final double W_MAX = 0.9;             // 最大惯性权重
    protected static final double W_MIN = 0.4;             // 最小惯性权重
    protected static final double C1 = 1.5;                // 认知学习率（个体最优）
    protected static final double C2 = 1.5;                // 社会学习率（全局最优）
    protected static final double V_MAX = 0.2;             // 最大速度（防止粒子飞出搜索空间）

    protected final Random random;
    protected final long seed;

    /**
     * 构造函数（带随机种子）
     * @param simulation CloudSim仿真引擎
     * @param seed 随机种子
     */
    public PSO_Broker(CloudSimPlus simulation, long seed) {
        super(simulation);
        this.seed = seed;
        this.random = new Random(seed);
    }

    /**
     * 构造函数（向后兼容，使用默认种子42）
     */
    public PSO_Broker(CloudSimPlus simulation) {
        this(simulation, 42L);
    }

    @Override
    protected void requestDatacentersToCreateWaitingCloudlets() {
        if (!getCloudletWaitingList().isEmpty()) {
            List<Cloudlet> cloudletList = new ArrayList<>(getCloudletWaitingList());
            List<Vm> vmList = getVmCreatedList();

            int M = cloudletList.size();  // 任务数
            int N = vmList.size();        // VM数

            System.out.println("\n==================== PSO算法开始 ===================");
            System.out.println("任务数: " + M);
            System.out.println("VM数: " + N);
            System.out.println("算法参数: PopSize=" + POPULATION_SIZE + ", MaxIter=" + MAX_ITERATIONS +
                    ", w=" + W_MAX + "→" + W_MIN + ", C1=" + C1 + ", C2=" + C2);
            System.out.println("====================================================\n");

            long startTime = System.currentTimeMillis();

            // PSO优化
            int[] bestSchedule = psoOptimize(cloudletList, vmList, M, N);

            // 应用最优调度方案
            for (int i = 0; i < M; i++) {
                Cloudlet cloudlet = cloudletList.get(i);
                Vm vm = vmList.get(bestSchedule[i]);
                cloudlet.setVm(vm);
            }

            // 计算最优Makespan
            double makespan = calculateMakespan(bestSchedule, cloudletList, vmList, M, N);

            long endTime = System.currentTimeMillis();

            System.out.println("==================== PSO算法完成 ===================");
            System.out.println("最优Makespan: " + String.format("%.4f", makespan));
            System.out.println("算法运行时间: " + (endTime - startTime) + " ms");
            System.out.println("====================================================\n");
        }

        super.requestDatacentersToCreateWaitingCloudlets();
    }

    /**
     * PSO优化主流程
     */
    protected int[] psoOptimize(List<Cloudlet> cloudletList, List<Vm> vmList, int M, int N) {
        // 初始化粒子群
        double[][] particles = new double[POPULATION_SIZE][M];      // 粒子位置（连续空间[0,1]）
        double[][] velocities = new double[POPULATION_SIZE][M];     // 粒子速度
        double[][] pBest = new double[POPULATION_SIZE][M];          // 个体最优位置
        double[] pBestFitness = new double[POPULATION_SIZE];        // 个体最优适应度
        double[] gBest = new double[M];                             // 全局最优位置
        double gBestFitness = Double.MAX_VALUE;                     // 全局最优适应度

        // 初始化粒子位置和速度
        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int j = 0; j < M; j++) {
                particles[i][j] = random.nextDouble();              // 位置 ∈ [0, 1]
                velocities[i][j] = (random.nextDouble() - 0.5) * 2 * V_MAX;  // 速度 ∈ [-V_MAX, V_MAX]
                pBest[i][j] = particles[i][j];
            }
            pBestFitness[i] = calculateFitness(particles[i], M, N, cloudletList, vmList);

            // 更新全局最优
            if (pBestFitness[i] < gBestFitness) {
                gBestFitness = pBestFitness[i];
                System.arraycopy(particles[i], 0, gBest, 0, M);
            }
        }

        // PSO迭代
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            // 计算当前迭代的惯性权重（线性衰减）
            double w = W_MAX - (W_MAX - W_MIN) * iter / MAX_ITERATIONS;

            // 更新每个粒子
            for (int i = 0; i < POPULATION_SIZE; i++) {
                for (int j = 0; j < M; j++) {
                    double r1 = random.nextDouble();
                    double r2 = random.nextDouble();

                    // PSO速度更新公式：
                    // v = w*v + c1*r1*(pBest - x) + c2*r2*(gBest - x)
                    velocities[i][j] = w * velocities[i][j]
                            + C1 * r1 * (pBest[i][j] - particles[i][j])
                            + C2 * r2 * (gBest[j] - particles[i][j]);

                    // 速度限制（防止粒子飞出搜索空间）
                    if (velocities[i][j] > V_MAX) velocities[i][j] = V_MAX;
                    if (velocities[i][j] < -V_MAX) velocities[i][j] = -V_MAX;

                    // 位置更新
                    particles[i][j] = particles[i][j] + velocities[i][j];

                    // 位置边界处理
                    if (particles[i][j] > 1.0) particles[i][j] = 1.0;
                    if (particles[i][j] < 0.0) particles[i][j] = 0.0;
                }

                // 计算适应度
                double fitness = calculateFitness(particles[i], M, N, cloudletList, vmList);

                // 更新个体最优
                if (fitness < pBestFitness[i]) {
                    pBestFitness[i] = fitness;
                    System.arraycopy(particles[i], 0, pBest[i], 0, M);
                }

                // 更新全局最优
                if (fitness < gBestFitness) {
                    gBestFitness = fitness;
                    System.arraycopy(particles[i], 0, gBest, 0, M);
                }
            }

            // 每20次迭代打印一次进度
            if ((iter + 1) % 20 == 0 || iter == 0) {
                System.out.println(String.format("Iter %3d/%d: gBest Makespan = %.4f, w = %.4f",
                        iter + 1, MAX_ITERATIONS, gBestFitness, w));
            }
        }

        // 将全局最优连续解转换为离散调度方案
        return continuousToDiscrete(gBest, N);
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
     * 将[0,1]连续空间映射到[0, N-1]离散VM索引
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
