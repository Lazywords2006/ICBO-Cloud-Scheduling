package com.icbo.research.benchmark;

import java.util.*;

/**
 * ICBO-Enhanced：基于CBO的改进算法（借鉴ERTH策略）
 *
 * ⚠️ 重要说明：
 * 本算法是CBO (Coyote and Badger Optimization) 的改进版本，
 * 不是ERTH算法的实现，而是借鉴了ERTH论文中的三个通用增强策略。
 *
 * 算法核心：CBO三阶段 (Searching → Encircling → Attacking)
 * 改进策略（来自ERTH论文的启发）：
 * 1. Bernoulli混沌映射初始化（λ=0.4）- 替代标准随机初始化
 * 2. 动态边界精英反向学习（EOBL）- αj, βj自适应边界
 * 3. 加权平均精英池机制（Top-3 + Xmean）- 多引导点策略
 * 4. 动态惯性权重（继承自ICBO）- ω_max=0.80, ω_min=0.10, k=3
 *
 * 算法命名逻辑：
 * - CBO：基础算法框架（三阶段优化）
 * - ICBO：CBO + 动态惯性权重
 * - ICBO-Enhanced：ICBO + ERTH启发的三大策略
 *
 * 参考文献：
 * - Khatab et al. (2025) - CBO算法（基础框架）
 * - Qin et al. (2024) - ERTH Scheduler（策略启发来源）
 *
 * @author ICBO Research Team
 * @version 2.0 (CBO-based with ERTH-inspired strategies)
 * @date 2025-12-10
 */
public class ICBO_E_Lite extends ICBO_Lite {

    // 精英池参数
    private static final int ELITE_POOL_SIZE = 5;
    private List<EliteSolution> elitePool;

    // 统计信息
    private int eoblImprovements = 0;

    /**
     * 精英解结构
     */
    private static class EliteSolution implements Comparable<EliteSolution> {
        double[] solution;
        double fitness;

        EliteSolution(double[] solution, double fitness) {
            this.solution = solution.clone();
            this.fitness = fitness;
        }

        @Override
        public int compareTo(EliteSolution other) {
            return Double.compare(this.fitness, other.fitness);
        }
    }

    // 种群数据
    private double[][] population;
    private double[] fitness;
    private double[] bestSolution;
    private double bestFitness;
    private final Random random;

    /**
     * 构造函数（带随机种子）
     * @param seed 随机种子
     */
    public ICBO_E_Lite(long seed) {
        super(seed);
        this.elitePool = new ArrayList<>();
        this.random = super.random;  // 使用父类的random实例
    }

    /**
     * 构造函数（向后兼容，使用默认种子42）
     */
    public ICBO_E_Lite() {
        super();
        this.elitePool = new ArrayList<>();
        this.random = super.random;  // 使用父类的random实例
    }

    @Override
    public double optimize(BenchmarkFunction function, int maxIterations) {
        int dimensions = function.getDimensions();
        population = new double[30][dimensions];
        fitness = new double[30];

        // 重置统计
        eoblImprovements = 0;
        elitePool.clear();

        // ⭐ 策略1：Bernoulli混沌初始化（借鉴ERTH，稳定且均匀）
        bernoulliChaoticInitialization(function);

        // ICBO-E迭代
        for (int t = 0; t < maxIterations; t++) {
            // Phase 1: Searching
            searchingPhase(function);

            // ⭐ 策略2：精英反向学习
            eliteOppositionBasedLearning(function);

            // Phase 2: Encircling
            encirclingPhase(function, t, maxIterations);

            // Phase 3: Attacking（使用ICBO动态惯性权重）
            icbo_attackingPhase(function, t, maxIterations);

            // ⭐ 策略3：更新精英池
            updateElitePool();

            // 更新全局最优解
            updateBestSolution(function);

            // 打印进度
            if ((t + 1) % 100 == 0 || t == 0) {
                System.out.println(String.format("  [ICBO-E Iter %4d/%d] Best=%.6e | EOBL=+%d | Elite=%d",
                    t + 1, maxIterations, bestFitness, eoblImprovements, elitePool.size()));
            }
        }

        return bestFitness;
    }

    @Override
    public String getName() {
        return "ICBO-Enhanced";
    }

    // ==================== 策略1：Bernoulli混沌初始化（借鉴ERTH） ====================

    /**
     * Bernoulli混沌映射初始化：使用Bernoulli Shift Map生成高质量初始种群
     *
     * 本策略借鉴自ERTH论文，但用于增强CBO算法的初始化阶段
     *
     * Bernoulli Shift Map公式：
     * xn+1 = { xn/(1-λ),           0 < xn < (1-λ)
     *        { (xn-(1-λ))/λ,  (1-λ) < xn < 1
     *
     * 优势（相比标准随机和Logistic混沌）：
     * - 在[0,1]区间均匀分布（无极端值）
     * - 遍历性：充分覆盖搜索空间
     * - 稳定性：λ=0.4远离分岔点，不会产生outliers
     *
     * 策略来源：Qin et al. (2024) - ERTH Scheduler（通用增强策略）
     */
    private void bernoulliChaoticInitialization(BenchmarkFunction function) {
        int dimensions = function.getDimensions();
        double lambda = 0.4;  // ERTH论文推荐参数
        double x0 = 0.7;      // 初始值

        for (int i = 0; i < 30; i++) {
            double xn = x0;

            for (int j = 0; j < dimensions; j++) {
                // Bernoulli Shift Map
                if (xn < (1 - lambda)) {
                    xn = xn / (1 - lambda);
                } else {
                    xn = (xn - (1 - lambda)) / lambda;
                }

                // 映射到搜索空间 [lb, ub]
                double range = function.getUpperBound() - function.getLowerBound();
                population[i][j] = function.getLowerBound() + xn * range;
            }

            fitness[i] = function.evaluate(population[i]);

            // 为下一个个体更新初始值（增加多样性）
            x0 = (x0 + 0.1) % 1.0;
            if (x0 < 0.1) x0 = 0.7;  // 避免接近0
        }

        // 初始化最优解
        int bestIdx = 0;
        for (int i = 1; i < 30; i++) {
            if (fitness[i] < fitness[bestIdx]) {
                bestIdx = i;
            }
        }
        bestSolution = population[bestIdx].clone();
        bestFitness = fitness[bestIdx];
    }

    // ==================== 策略2：动态边界精英反向学习（借鉴ERTH） ====================

    /**
     * 动态边界精英反向学习（EOBL）：使用动态边界进行反向学习
     *
     * 本策略借鉴自ERTH论文，用于增强CBO的探索能力
     *
     * ERTH公式：
     * X̄e_i,j = K × (αj + βj) - Xe_i,j
     *
     * 其中：
     * - αj = max(Xi,j)：当前种群j维最大值（动态）
     * - βj = min(Xi,j)：当前种群j维最小值（动态）
     * - K ∈ (0,1)：随机系数（增加扰动）
     *
     * 优势（相比固定边界lb+ub）：
     * - 动态边界随迭代自适应调整
     * - 反向解在当前搜索范围内，不会超出
     * - K系数提供额外随机性，避免对称陷阱
     *
     * 策略来源：Qin et al. (2024) - ERTH Scheduler（通用增强策略）
     */
    private void eliteOppositionBasedLearning(BenchmarkFunction function) {
        int dimensions = function.getDimensions();
        double[] oppositeSolution = new double[dimensions];

        for (int j = 0; j < dimensions; j++) {
            // 计算动态边界 αj, βj
            double alpha = population[0][j];
            double beta = population[0][j];

            for (int i = 1; i < 30; i++) {
                alpha = Math.max(alpha, population[i][j]);
                beta = Math.min(beta, population[i][j]);
            }

            // ERTH公式：X̄e_i,j = K × (αj + βj) - Xe_i,j
            double K = random.nextDouble();  // (0, 1)
            oppositeSolution[j] = K * (alpha + beta) - bestSolution[j];

            // 边界处理（确保在搜索空间内）
            if (oppositeSolution[j] < function.getLowerBound()) {
                oppositeSolution[j] = function.getLowerBound();
            } else if (oppositeSolution[j] > function.getUpperBound()) {
                oppositeSolution[j] = function.getUpperBound();
            }
        }

        double oppositeFitness = function.evaluate(oppositeSolution);

        if (oppositeFitness < bestFitness) {
            bestSolution = oppositeSolution.clone();
            bestFitness = oppositeFitness;
            eoblImprovements++;
        }
    }

    // ==================== 策略3：精英池机制 + 加权平均位置（借鉴ERTH） ====================

    /**
     * 更新精英池，维护Top-5历史最优解
     */
    private void updateElitePool() {
        EliteSolution elite = new EliteSolution(bestSolution, bestFitness);
        elitePool.add(elite);

        Collections.sort(elitePool);

        if (elitePool.size() > ELITE_POOL_SIZE) {
            elitePool.remove(elitePool.size() - 1);
        }
    }

    /**
     * 计算加权平均精英位置（借鉴ERTH策略）
     *
     * 本策略借鉴自ERTH论文，用于增强CBO的引导多样性
     *
     * ERTH公式：
     * Xt_mean = Σ(i=1 to Num/2) ωi·Xi^t
     * ωi = ln(Num/2+0.5)-ln(i) / Σ[ln(Num/2+0.5)-ln(i)]
     *
     * 优势：
     * - 提供额外的引导点，增加搜索方向多样性
     * - 对数权重使前15个个体都有贡献，避免单点引导
     * - 加权平均位置在优秀区域的"中心"，有助于开发
     *
     * 策略来源：Qin et al. (2024) - ERTH Scheduler（通用增强策略）
     */
    private double[] calculateWeightedMeanElite() {
        int dimensions = bestSolution.length;
        double[] meanPosition = new double[dimensions];

        int halfPop = 15;  // Num/2 = 30/2
        double[] weights = new double[halfPop];
        double weightSum = 0.0;

        // 计算对数权重
        for (int i = 0; i < halfPop; i++) {
            weights[i] = Math.log(halfPop + 0.5) - Math.log(i + 1);
            weightSum += weights[i];
        }

        // 归一化权重
        for (int i = 0; i < halfPop; i++) {
            weights[i] /= weightSum;
        }

        // 对种群进行排序（按适应度）
        int[] sortedIndices = new int[30];
        for (int i = 0; i < 30; i++) {
            sortedIndices[i] = i;
        }

        // 简单冒泡排序（因为只需要前15个）
        for (int i = 0; i < halfPop; i++) {
            for (int j = i + 1; j < 30; j++) {
                if (fitness[sortedIndices[j]] < fitness[sortedIndices[i]]) {
                    int temp = sortedIndices[i];
                    sortedIndices[i] = sortedIndices[j];
                    sortedIndices[j] = temp;
                }
            }
        }

        // 加权求和（前15个最优个体）
        for (int j = 0; j < dimensions; j++) {
            meanPosition[j] = 0.0;
            for (int i = 0; i < halfPop; i++) {
                int idx = sortedIndices[i];
                meanPosition[j] += weights[i] * population[idx][j];
            }
        }

        return meanPosition;
    }

    // ==================== Phase 1 & 2 & 3（复制父类） ====================

    private void searchingPhase(BenchmarkFunction function) {
        int dimensions = function.getDimensions();

        for (int i = 0; i < 30; i++) {
            int preyIdx = random.nextInt(30);
            if (preyIdx == i) preyIdx = (i + 1) % 30;

            for (int j = 0; j < dimensions; j++) {
                double d = Math.abs(population[preyIdx][j] - population[i][j]);
                double r = random.nextDouble() - 0.5;
                double step = r * Math.tanh(d) * (population[preyIdx][j] - population[i][j]);
                population[i][j] += step;

                if (population[i][j] < function.getLowerBound()) {
                    population[i][j] = function.getLowerBound();
                } else if (population[i][j] > function.getUpperBound()) {
                    population[i][j] = function.getUpperBound();
                }
            }

            fitness[i] = function.evaluate(population[i]);
        }
    }

    private void encirclingPhase(BenchmarkFunction function, int t, int maxIterations) {
        int dimensions = function.getDimensions();
        double theta = 2.0 * Math.PI * t / maxIterations;
        double cosTheta = Math.cos(theta);
        double sinTheta = Math.sin(theta);

        for (int i = 0; i < 30; i++) {
            for (int j = 0; j < dimensions - 1; j += 2) {
                double x1 = population[i][j];
                double x2 = population[i][j + 1];
                double x1_new = cosTheta * x1 - sinTheta * x2;
                double x2_new = sinTheta * x1 + cosTheta * x2;
                population[i][j] = x1_new;
                population[i][j + 1] = x2_new;
            }

            if (dimensions % 2 == 1) {
                int lastIdx = dimensions - 1;
                double a = 2.0 * (1.0 - (double) t / maxIterations);
                population[i][lastIdx] += a * (bestSolution[lastIdx] - population[i][lastIdx]);
            }

            for (int j = 0; j < dimensions; j++) {
                if (population[i][j] < function.getLowerBound()) {
                    population[i][j] = function.getLowerBound();
                } else if (population[i][j] > function.getUpperBound()) {
                    population[i][j] = function.getUpperBound();
                }
            }

            fitness[i] = function.evaluate(population[i]);
        }
    }

    private void icbo_attackingPhase(BenchmarkFunction function, int t, int maxIterations) {
        int dimensions = function.getDimensions();
        double omega = calculateDynamicOmega(t, maxIterations);

        for (int i = 0; i < 30; i++) {
            // ⭐ 自适应精英池引导策略（借鉴ERTH多引导点机制）：
            // - 早期30%（探索）→ 中期20%（平衡）→ 后期10%（收敛）
            // - 25%概率使用加权平均位置（ERTH的第4个引导点思想）
            double eliteProbability = calculateEliteProbability(t, maxIterations);
            double[] targetSolution;

            if (elitePool.size() >= 3 && random.nextDouble() < eliteProbability) {
                // 使用精英池引导（借鉴ERTH的多引导点策略）
                if (random.nextDouble() < 0.25) {
                    // 25%概率：使用加权平均精英位置（类似ERTH的Xmean）
                    targetSolution = calculateWeightedMeanElite();
                } else {
                    // 75%概率：从Top-3精英池中随机选择（类似ERTH的Xbest, Xsecond, Xthird）
                    int eliteIdx = random.nextInt(Math.min(3, elitePool.size()));
                    targetSolution = elitePool.get(eliteIdx).solution;
                }
            } else {
                // 标准ICBO策略：使用全局最优解
                targetSolution = bestSolution;
            }

            for (int j = 0; j < dimensions; j++) {
                population[i][j] = omega * population[i][j] + (1.0 - omega) * targetSolution[j];

                if (population[i][j] < function.getLowerBound()) {
                    population[i][j] = function.getLowerBound();
                } else if (population[i][j] > function.getUpperBound()) {
                    population[i][j] = function.getUpperBound();
                }
            }

            fitness[i] = function.evaluate(population[i]);
        }
    }

    private double calculateDynamicOmega(int t, int maxIterations) {
        double ratio = 1.0 - (double) t / maxIterations;
        return 0.10 + (0.80 - 0.10) * Math.pow(ratio, 3);
    }

    /**
     * 计算自适应精英池使用概率
     *
     * 策略：随迭代进度递减
     * - 前30%迭代（t < 300）：30%概率 - 强探索，适合多峰函数
     * - 中期40%迭代（300 ≤ t < 700）：20%概率 - 平衡探索与开发
     * - 后30%迭代（t ≥ 700）：10%概率 - 专注收敛，避免Griewank退化
     *
     * 目的：解决固定30%在Griewank函数上后期过度探索导致退化的问题
     */
    private double calculateEliteProbability(int t, int maxIterations) {
        double progress = (double) t / maxIterations;
        if (progress < 0.3) {
            return 0.30;  // 早期：强探索
        } else if (progress < 0.7) {
            return 0.20;  // 中期：平衡
        } else {
            return 0.10;  // 后期：收敛
        }
    }

    private void updateBestSolution(BenchmarkFunction function) {
        for (int i = 0; i < 30; i++) {
            if (fitness[i] < bestFitness) {
                bestFitness = fitness[i];
                bestSolution = population[i].clone();
            }
        }
    }
}
