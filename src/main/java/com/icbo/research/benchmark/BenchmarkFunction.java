package com.icbo.research.benchmark;

import java.util.Random;

/**
 * CEC2017基准测试函数抽象基类
 *
 * 所有基准函数继承此类，实现evaluate()方法
 * 提供统一的接口用于元启发式算法测试
 *
 * @author ICBO Research Team
 * @version 1.0
 * @date 2025-12-10
 */
public abstract class BenchmarkFunction {

    /** 函数名称（如 "Sphere", "Rastrigin"） */
    protected final String name;

    /** 函数ID（F1-F10） */
    protected final int id;

    /** 问题维度（默认30维） */
    protected final int dimensions;

    /** 搜索空间下界 */
    protected final double lowerBound;

    /** 搜索空间上界 */
    protected final double upperBound;

    /** 理论全局最优值 */
    protected final double globalOptimum;

    /** 函数类型（Unimodal, Multimodal, Quadratic） */
    protected final FunctionType type;

    /** 随机数生成器 */
    protected static final Random random = new Random(42);

    /**
     * 构造函数
     *
     * @param id 函数ID（1-10）
     * @param name 函数名称
     * @param dimensions 问题维度
     * @param lowerBound 搜索空间下界
     * @param upperBound 搜索空间上界
     * @param globalOptimum 理论全局最优值
     * @param type 函数类型
     */
    public BenchmarkFunction(int id, String name, int dimensions,
                            double lowerBound, double upperBound,
                            double globalOptimum, FunctionType type) {
        this.id = id;
        this.name = name;
        this.dimensions = dimensions;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.globalOptimum = globalOptimum;
        this.type = type;
    }

    /**
     * 评估函数值（核心方法，由子类实现）
     *
     * @param x 决策变量向量（长度=dimensions）
     * @return 函数值（越小越优）
     * @throws IllegalArgumentException 如果x长度不等于dimensions
     */
    public abstract double evaluate(double[] x);

    /**
     * 生成随机初始解（在搜索空间内）
     *
     * @return 随机解向量
     */
    public double[] generateRandomSolution() {
        double[] solution = new double[dimensions];
        for (int i = 0; i < dimensions; i++) {
            solution[i] = lowerBound + random.nextDouble() * (upperBound - lowerBound);
        }
        return solution;
    }

    /**
     * 边界检查（确保解在搜索空间内）
     *
     * @param x 待检查的解
     */
    public void checkBounds(double[] x) {
        for (int i = 0; i < x.length; i++) {
            if (x[i] < lowerBound) {
                x[i] = lowerBound;
            } else if (x[i] > upperBound) {
                x[i] = upperBound;
            }
        }
    }

    /**
     * 计算解与全局最优的误差
     *
     * @param fitness 当前适应度值
     * @return 误差值（|fitness - globalOptimum|）
     */
    public double calculateError(double fitness) {
        return Math.abs(fitness - globalOptimum);
    }

    /**
     * 判断是否达到全局最优（误差 < 1e-8）
     *
     * @param fitness 当前适应度值
     * @return true如果达到全局最优
     */
    public boolean isGlobalOptimumReached(double fitness) {
        return calculateError(fitness) < 1e-8;
    }

    /**
     * 获取函数描述字符串
     *
     * @return 描述信息
     */
    public String getDescription() {
        return String.format("F%d: %s (%s) | Dim=%d | Range=[%.1f,%.1f] | Optimum=%.4f",
                            id, name, type, dimensions, lowerBound, upperBound, globalOptimum);
    }

    // ==================== Getters ====================

    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }

    public int getDimensions() {
        return dimensions;
    }

    public double getLowerBound() {
        return lowerBound;
    }

    public double getUpperBound() {
        return upperBound;
    }

    public double getGlobalOptimum() {
        return globalOptimum;
    }

    public FunctionType getType() {
        return type;
    }

    /**
     * 函数类型枚举
     */
    public enum FunctionType {
        UNIMODAL("Unimodal"),           // 单峰函数
        MULTIMODAL("Multimodal"),       // 多峰函数
        QUADRATIC("Quadratic");         // 二次函数

        private final String description;

        FunctionType(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    /**
     * 验证解的维度是否正确
     *
     * @param x 待验证的解
     * @throws IllegalArgumentException 如果维度不匹配
     */
    protected void validateDimensions(double[] x) {
        if (x.length != dimensions) {
            throw new IllegalArgumentException(
                String.format("Expected %d dimensions, but got %d", dimensions, x.length)
            );
        }
    }
}
