package com.icbo.research.benchmark;

import java.util.Random;

/**
 * ICBO轻量级版本 - 专用于CEC2017基准测试
 *
 * 基于ICBO_Broker的核心算法，移除CloudSim依赖
 * 相比CBO_Lite，Phase 3使用动态惯性权重机制
 *
 * 核心改进：
 * - ω_max = 0.80 (初始惯性权重，前期探索)
 * - ω_min = 0.10 (最小惯性权重，后期强收敛)
 * - k = 3 (三次衰减指数，关键突破！)
 *
 * 动态惯性权重公式：
 * ω(t) = ω_min + (ω_max - ω_min) * (1 - t/T_max)^k
 *
 * @author ICBO Research Team
 * @version 1.0
 * @date 2025-12-10
 */
public class ICBO_Lite extends CBO_Lite {

    // ICBO最优参数
    private static final double OMEGA_MAX = 0.80;
    private static final double OMEGA_MIN = 0.10;
    private static final int K = 3;

    // 种群数据（从父类继承）
    private double[][] population;
    private double[] fitness;
    private double[] bestSolution;
    private double bestFitness;
    protected final Random random;  // 改为protected，允许子类访问

    /**
     * 构造函数（带随机种子）
     * @param seed 随机种子
     */
    public ICBO_Lite(long seed) {
        super(seed);
        this.random = super.random;  // 使用父类的random实例
    }

    /**
     * 构造函数（向后兼容，使用默认种子42）
     */
    public ICBO_Lite() {
        super();
        this.random = super.random;  // 使用父类的random实例
    }

    @Override
    public double optimize(BenchmarkFunction function, int maxIterations) {
        int dimensions = function.getDimensions();
        population = new double[30][dimensions];  // POPULATION_SIZE = 30
        fitness = new double[30];

        // 初始化种群
        initializePopulation(function);

        // ICBO迭代
        for (int t = 0; t < maxIterations; t++) {
            // Phase 1: Searching（调用父类CBO方法）
            searchingPhase(function);

            // Phase 2: Encircling（调用父类CBO方法）
            encirclingPhase(function, t, maxIterations);

            // Phase 3: Attacking（ICBO改进 - 动态惯性权重）
            icbo_attackingPhase(function, t, maxIterations);

            // 更新全局最优解
            updateBestSolution(function);

            // 打印进度（每100次迭代）
            if ((t + 1) % 100 == 0 || t == 0) {
                double omega = calculateDynamicOmega(t, maxIterations);
                System.out.println(String.format("  [ICBO Iter %4d/%d] Best=%.6e | ω=%.4f",
                    t + 1, maxIterations, bestFitness, omega));
            }
        }

        return bestFitness;
    }

    @Override
    public String getName() {
        return "ICBO";
    }

    // ==================== 初始化（复制父类方法） ====================

    private void initializePopulation(BenchmarkFunction function) {
        int dimensions = function.getDimensions();

        for (int i = 0; i < 30; i++) {
            for (int j = 0; j < dimensions; j++) {
                double value = function.getLowerBound() +
                              random.nextDouble() * (function.getUpperBound() - function.getLowerBound());
                population[i][j] = value;
            }
            fitness[i] = function.evaluate(population[i]);
        }

        int bestIdx = 0;
        for (int i = 1; i < 30; i++) {
            if (fitness[i] < fitness[bestIdx]) {
                bestIdx = i;
            }
        }
        bestSolution = population[bestIdx].clone();
        bestFitness = fitness[bestIdx];
    }

    // ==================== Phase 1 & 2（复制父类方法） ====================

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

    // ==================== Phase 3: ICBO改进的Attacking ====================

    /**
     * ICBO改进的Attacking Phase
     * 使用动态惯性权重：x^{i+1} = ω(t) * x^i + (1 - ω(t)) * x_best
     */
    private void icbo_attackingPhase(BenchmarkFunction function, int t, int maxIterations) {
        int dimensions = function.getDimensions();
        double omega = calculateDynamicOmega(t, maxIterations);

        for (int i = 0; i < 30; i++) {
            for (int j = 0; j < dimensions; j++) {
                // 动态惯性权重公式
                population[i][j] = omega * population[i][j] + (1.0 - omega) * bestSolution[j];

                // 边界检查
                if (population[i][j] < function.getLowerBound()) {
                    population[i][j] = function.getLowerBound();
                } else if (population[i][j] > function.getUpperBound()) {
                    population[i][j] = function.getUpperBound();
                }
            }

            // 重新评估适应度
            fitness[i] = function.evaluate(population[i]);
        }
    }

    // ==================== 动态惯性权重计算 ====================

    /**
     * 计算动态惯性权重
     * ω(t) = ω_min + (ω_max - ω_min) * (1 - t/T_max)^k
     */
    private double calculateDynamicOmega(int t, int maxIterations) {
        double ratio = 1.0 - (double) t / maxIterations;
        return OMEGA_MIN + (OMEGA_MAX - OMEGA_MIN) * Math.pow(ratio, K);
    }

    // ==================== 更新最优解 ====================

    private void updateBestSolution(BenchmarkFunction function) {
        for (int i = 0; i < 30; i++) {
            if (fitness[i] < bestFitness) {
                bestFitness = fitness[i];
                bestSolution = population[i].clone();
            }
        }
    }
}
