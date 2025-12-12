package com.icbo.research;

import com.icbo.research.utils.ConvergenceRecord;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.vms.Vm;

import java.util.*;

/**
 * GWO_Broker - Grey Wolf Optimizer for Cloud Task Scheduling
 *
 * 灰狼优化算法
 *
 * 算法特点：
 * - 模拟灰狼狩猎行为和社会等级制度
 * - 三级等级：Alpha（α）> Beta（β）> Delta（δ）> Omega（ω）
 * - 包围、狩猎、攻击三阶段
 *
 * 参数配置（来自CBO论文）：
 * - Encircling coefficient (a) = 2 → 0（线性收敛）
 * - Random vectors r1, r2 ∈ [0, 1]
 * - Population Size = 30
 * - Max Iterations = 100
 *
 * 经典参考：
 * - Mirjalili et al. (2014): "Grey Wolf Optimizer"
 * - Khatab et al. (2025): CBO论文使用GWO作为对比算法
 */
public class GWO_Broker extends DatacenterBrokerSimple {

    // GWO参数
    protected static final int POPULATION_SIZE = 30;       // 狼群数量
    protected static final int MAX_ITERATIONS = 100;       // 最大迭代次数

    protected final Random random;
    protected final long seed;
    private ConvergenceRecord convergenceRecord;  // ✅ Day 3.1新增：收敛记录器

    /**
     * 构造函数（带随机种子）
     * @param simulation CloudSim仿真引擎
     * @param seed 随机种子
     */
    public GWO_Broker(CloudSimPlus simulation, long seed) {
        super(simulation);
        this.seed = seed;
        this.random = new Random(seed);
    }

    /**
     * 构造函数（向后兼容，使用默认种子42）
     */
    public GWO_Broker(CloudSimPlus simulation) {
        this(simulation, 42L);
    }

    @Override
    protected void requestDatacentersToCreateWaitingCloudlets() {
        if (!getCloudletWaitingList().isEmpty()) {
            List<Cloudlet> cloudletList = new ArrayList<>(getCloudletWaitingList());
            List<Vm> vmList = getVmCreatedList();

            int M = cloudletList.size();  // 任务数
            int N = vmList.size();        // VM数

            System.out.println("\n==================== GWO算法开始 ===================");
            System.out.println("任务数: " + M);
            System.out.println("VM数: " + N);
            System.out.println("算法参数: PopSize=" + POPULATION_SIZE + ", MaxIter=" + MAX_ITERATIONS +
                    ", a=2→0（线性收敛）");
            System.out.println("====================================================\n");

            long startTime = System.currentTimeMillis();

            // GWO优化
            int[] bestSchedule = gwoOptimize(cloudletList, vmList, M, N);

            // 应用最优调度方案
            for (int i = 0; i < M; i++) {
                Cloudlet cloudlet = cloudletList.get(i);
                Vm vm = vmList.get(bestSchedule[i]);
                cloudlet.setVm(vm);
            }

            // 计算最优Makespan
            double makespan = calculateMakespan(bestSchedule, cloudletList, vmList, M, N);

            long endTime = System.currentTimeMillis();

            System.out.println("==================== GWO算法完成 ===================");
            System.out.println("最优Makespan: " + String.format("%.4f", makespan));
            System.out.println("算法运行时间: " + (endTime - startTime) + " ms");
            System.out.println("====================================================\n");
        }

        super.requestDatacentersToCreateWaitingCloudlets();
    }

    /**
     * GWO优化主流程
     */
    protected int[] gwoOptimize(List<Cloudlet> cloudletList, List<Vm> vmList, int M, int N) {
        // 初始化狼群
        double[][] wolves = new double[POPULATION_SIZE][M];  // 狼群位置（连续空间[0,1]）
        double[] fitness = new double[POPULATION_SIZE];      // 适应度

        // ✅ Day 3.1新增：创建收敛记录器
        String scale = String.format("M%d", M);
        this.convergenceRecord = new ConvergenceRecord("GWO", scale, this.seed);

        // Alpha, Beta, Delta狼（社会等级前三）
        double[] alphaPos = new double[M];
        double alphaScore = Double.MAX_VALUE;

        double[] betaPos = new double[M];
        double betaScore = Double.MAX_VALUE;

        double[] deltaPos = new double[M];
        double deltaScore = Double.MAX_VALUE;

        // 初始化狼群位置
        for (int i = 0; i < POPULATION_SIZE; i++) {
            for (int j = 0; j < M; j++) {
                wolves[i][j] = random.nextDouble();  // 位置 ∈ [0, 1]
            }
            fitness[i] = calculateFitness(wolves[i], M, N, cloudletList, vmList);

            // 更新Alpha, Beta, Delta
            if (fitness[i] < alphaScore) {
                deltaScore = betaScore;
                System.arraycopy(betaPos, 0, deltaPos, 0, M);

                betaScore = alphaScore;
                System.arraycopy(alphaPos, 0, betaPos, 0, M);

                alphaScore = fitness[i];
                System.arraycopy(wolves[i], 0, alphaPos, 0, M);
            } else if (fitness[i] < betaScore) {
                deltaScore = betaScore;
                System.arraycopy(betaPos, 0, deltaPos, 0, M);

                betaScore = fitness[i];
                System.arraycopy(wolves[i], 0, betaPos, 0, M);
            } else if (fitness[i] < deltaScore) {
                deltaScore = fitness[i];
                System.arraycopy(wolves[i], 0, deltaPos, 0, M);
            }
        }

        // GWO迭代
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            // 计算包围系数a（从2线性减少到0）
            double a = 2.0 - iter * (2.0 / MAX_ITERATIONS);

            // 更新每只狼的位置
            for (int i = 0; i < POPULATION_SIZE; i++) {
                for (int j = 0; j < M; j++) {
                    // 随机向量
                    double r1 = random.nextDouble();
                    double r2 = random.nextDouble();

                    // Alpha狼引导的位置
                    double A1 = 2 * a * r1 - a;
                    double C1 = 2 * r2;
                    double D_alpha = Math.abs(C1 * alphaPos[j] - wolves[i][j]);
                    double X1 = alphaPos[j] - A1 * D_alpha;

                    // Beta狼引导的位置
                    r1 = random.nextDouble();
                    r2 = random.nextDouble();
                    double A2 = 2 * a * r1 - a;
                    double C2 = 2 * r2;
                    double D_beta = Math.abs(C2 * betaPos[j] - wolves[i][j]);
                    double X2 = betaPos[j] - A2 * D_beta;

                    // Delta狼引导的位置
                    r1 = random.nextDouble();
                    r2 = random.nextDouble();
                    double A3 = 2 * a * r1 - a;
                    double C3 = 2 * r2;
                    double D_delta = Math.abs(C3 * deltaPos[j] - wolves[i][j]);
                    double X3 = deltaPos[j] - A3 * D_delta;

                    // 计算新位置（三个领导者的平均位置）
                    wolves[i][j] = (X1 + X2 + X3) / 3.0;

                    // 位置边界处理
                    if (wolves[i][j] > 1.0) wolves[i][j] = 1.0;
                    if (wolves[i][j] < 0.0) wolves[i][j] = 0.0;
                }

                // 计算适应度
                fitness[i] = calculateFitness(wolves[i], M, N, cloudletList, vmList);

                // 更新Alpha, Beta, Delta
                if (fitness[i] < alphaScore) {
                    deltaScore = betaScore;
                    System.arraycopy(betaPos, 0, deltaPos, 0, M);

                    betaScore = alphaScore;
                    System.arraycopy(alphaPos, 0, betaPos, 0, M);

                    alphaScore = fitness[i];
                    System.arraycopy(wolves[i], 0, alphaPos, 0, M);
                } else if (fitness[i] < betaScore) {
                    deltaScore = betaScore;
                    System.arraycopy(betaPos, 0, deltaPos, 0, M);

                    betaScore = fitness[i];
                    System.arraycopy(wolves[i], 0, betaPos, 0, M);
                } else if (fitness[i] < deltaScore) {
                    deltaScore = fitness[i];
                    System.arraycopy(wolves[i], 0, deltaPos, 0, M);
                }
            }

            // ✅ Day 3.1新增：记录收敛曲线
            convergenceRecord.recordIteration(iter, alphaScore);

            // 每20次迭代打印一次进度
            if ((iter + 1) % 20 == 0 || iter == 0) {
                System.out.println(String.format("Iter %3d/%d: Alpha Makespan = %.4f, a = %.4f",
                        iter + 1, MAX_ITERATIONS, alphaScore, a));
            }
        }

        // ✅ Day 3.1新增：导出收敛曲线到CSV
        convergenceRecord.exportToCSV("results/");

        // 将Alpha狼的连续解转换为离散调度方案
        return continuousToDiscrete(alphaPos, N);
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
