# ICBO-Enhanced Algorithm Flowchart

**Purpose**: This document provides a detailed textual description of the ICBO-Enhanced algorithm flow for creating a visual flowchart using tools like draw.io, Visio, or PowerPoint.

---

## Overall Algorithm Flow

```
[START]
   ↓
[Initialize Parameters]
   ↓
[Bernoulli Chaotic Initialization]
   ↓
[Main Loop: t = 0 to MAX_ITERATIONS-1] ──┐
   ↓                                      │
[Phase 1: Searching] (Dynamic tanh)      │
   ↓                                      │
[EOBL #1] (After Searching)              │
   ↓                                      │
[Phase 2: Encircling] (Rotation Matrix)  │
   ↓                                      │
[EOBL #2] (After Encircling)             │
   ↓                                      │
[Phase 3: Attacking] (Dynamic Inertia)   │
   ↓                                      │
[Update Elite Pool] (Top-15 Weighted)    │
   ↓                                      │
[Update Global Best]                     │
   ↓                                      │
[Record Convergence]                     │
   ↓                                      │
[t++] ───────────────────────────────────┘
   ↓
[Return Best Solution]
   ↓
[END]
```

---

## Detailed Component Flowcharts

### 1. Bernoulli Chaotic Initialization

```
[START Initialization]
   ↓
[Set λ = 0.4, x₀ = 0.7]
   ↓
[For each individual i = 0 to P-1] ──┐
   ↓                                  │
   [xₙ ← x₀]                          │
   ↓                                  │
   [For each dimension j = 0 to M-1]─┐│
      ↓                               ││
      [If xₙ < (1-λ)]                 ││
         ├─[Yes]→ [xₙ ← xₙ / (1-λ)]   ││
         └─[No]──→ [xₙ ← (xₙ-1+λ)/λ]  ││
      ↓                               ││
      [population[i][j] ← xₙ]         ││
      ↓                               ││
   [j++] ──────────────────────────── ┘│
   ↓                                   │
   [Calculate fitness[i]]              │
   ↓                                   │
[i++] ─────────────────────────────────┘
   ↓
[Find initial bestSolution]
   ↓
[END Initialization]
```

### 2. Phase 1: Searching Phase (Dynamic Searching)

```
[START Searching Phase]
   ↓
[For each individual i = 0 to P-1] ──┐
   ↓                                  │
   [For each dimension j = 0 to M-1]─┐│
      ↓                               ││
      [d ← |preyPosition[j] - population[i][j]|]
      ↓                               ││
      [r ← random(0, 1)]              ││
      ↓                               ││
      [Δ ← r × tanh(d) × (preyPosition[j] - population[i][j])]
      ↓                               ││
      [population[i][j] ← population[i][j] + Δ]
      ↓                               ││
      [Clamp to [0, 1]]               ││
      ↓                               ││
   [j++] ──────────────────────────── ┘│
   ↓                                   │
   [Recalculate fitness[i]]            │
   ↓                                   │
   [Update bestSolution if improved]   │
   ↓                                   │
[i++] ─────────────────────────────────┘
   ↓
[END Searching Phase]
```

### 3. Elite Opposition-Based Learning (EOBL)

```
[START EOBL]
   ↓
[Calculate dynamic boundaries αⱼ, βⱼ] ───────┐
   ↓                                          │
   [For each dimension j = 0 to M-1] ──┐     │
      ↓                                 │     │
      [αⱼ ← min(population[:, j])]     │     │
      [βⱼ ← max(population[:, j])]     │     │
      ↓                                 │     │
   [j++] ────────────────────────────── ┘     │
   ↓                                          │
[For each individual i = 0 to P-1] ──┐        │
   ↓                                  │       │
   [Generate opposition solution]     │       │
   ↓                                  │       │
   [For each dimension j = 0 to M-1]─┐│       │
      ↓                               ││       │
      [opposition[j] ← αⱼ + βⱼ - population[i][j]]
      ↓                               ││       │
      [Clamp to [0, 1]]               ││       │
      ↓                               ││       │
   [j++] ──────────────────────────── ┘│       │
   ↓                                   │       │
   [fitness_opp ← calculate(opposition)]│      │
   ↓                                   │       │
   [If fitness_opp < fitness[i]]       │       │
      ├─[Yes]→ [population[i] ← opposition]    │
      │        [fitness[i] ← fitness_opp]      │
      │        [eoblImprovements++]            │
      └─[No]──→ [Keep current solution]        │
   ↓                                   │       │
[i++] ─────────────────────────────────┘       │
   ↓
[Update bestSolution]
   ↓
[END EOBL]
```

### 4. Phase 2: Encircling Phase (Dynamic Encircling)

```
[START Encircling Phase]
   ↓
[θ ← 2π × t / T_max]
   ↓
[For each individual i = 0 to P-1] ──┐
   ↓                                  │
   [For pairs (j, j+1) where j is even]─┐
      ↓                                  │
      [x ← population[i][j]]             │
      [y ← population[i][j+1]]           │
      ↓                                  │
      [Apply Rotation Matrix]            │
      [x' ← x × cos(θ) - y × sin(θ)]     │
      [y' ← x × sin(θ) + y × cos(θ)]     │
      ↓                                  │
      [population[i][j] ← x']            │
      [population[i][j+1] ← y']          │
      ↓                                  │
      [Clamp to [0, 1]]                  │
      ↓                                  │
   [j += 2] ──────────────────────────── ┘
   ↓
   [For odd dimension M (if exists)]
      ↓
      [Linear convergence]
      [population[i][M-1] ← (population[i][M-1] + bestSolution[M-1]) / 2]
   ↓                                   │
   [Recalculate fitness[i]]            │
   ↓                                   │
   [Update bestSolution if improved]   │
   ↓                                   │
[i++] ─────────────────────────────────┘
   ↓
[END Encircling Phase]
```

### 5. Phase 3: Attacking Phase (Dynamic Inertia Weight)

```
[START Attacking Phase]
   ↓
[Calculate dynamic ω(t)]
   ↓
   [progress ← 1 - t / T_max]
   ↓
   [ω(t) ← ω_min + (ω_max - ω_min) × progress^k]
   ↓
   [If ω(t) < ω_min] ─→ [ω(t) ← ω_min]
   ↓
[Select target solution] ────────────────────────┐
   ↓                                             │
   [If elitePool.size() ≥ 3 AND random < 0.3]   │
      ├─[Yes]→ [target ← random elite from top-3]│
      └─[No]──→ [target ← bestSolution]          │
   ↓                                             │
[For each individual i = 0 to P-1] ──┐           │
   ↓                                  │          │
   [For each dimension j = 0 to M-1]─┐│          │
      ↓                               ││          │
      [population[i][j] ← ω(t) × population[i][j] + (1-ω(t)) × target[j]]
      ↓                               ││          │
      [Clamp to [0, 1]]               ││          │
      ↓                               ││          │
   [j++] ──────────────────────────── ┘│          │
   ↓                                   │          │
   [Recalculate fitness[i]]            │          │
   ↓                                   │          │
   [Update bestSolution if improved]   │          │
   ↓                                   │          │
[i++] ─────────────────────────────────┘          │
   ↓
[END Attacking Phase]
```

### 6. Elite Pool Update

```
[START Elite Pool Update]
   ↓
[Create list of all solutions with fitness]
   ↓
[Sort by fitness (ascending)] ─→ O(P log P)
   ↓
[Keep top-15 solutions]
   ↓
[If elitePool.size() > 15]
   ├─[Yes]→ [Remove worst solutions]
   └─[No]──→ [Keep all]
   ↓
[END Elite Pool Update]
```

### 7. Convergence Recording

```
[START Convergence Recording]
   ↓
[iterationBestFitness[t] ← bestFitness]
   ↓
[If (t+1) % 10 == 0]
   ├─[Yes]→ [Print progress: Iteration t, Best Fitness]
   └─[No]──→ [Skip logging]
   ↓
[END Convergence Recording]
```

---

## Algorithm Pseudo-code

```text
Algorithm: ICBO-Enhanced
Input: M (tasks), N (VMs), P (population), I (iterations)
Output: bestSolution (task-to-VM mapping), bestFitness (makespan)

1: // ===== Initialization =====
2: population ← BernoulliChaoticInitialize(P, M, λ=0.4)
3: fitness ← EvaluatePopulation(population)
4: bestSolution ← FindBest(population, fitness)
5: elitePool ← []
6:
7: // ===== Main Loop =====
8: for t = 0 to I-1 do
9:
10:   // Phase 1: Searching
11:   for i = 0 to P-1 do
12:      for j = 0 to M-1 do
13:         d ← |preyPosition[j] - population[i][j]|
14:         r ← random(0, 1)
15:         population[i][j] ← population[i][j] + r × tanh(d) × (preyPosition[j] - population[i][j])
16:      end for
17:      fitness[i] ← Evaluate(population[i])
18:   end for
19:   UpdateBestSolution()
20:
21:   // EOBL #1
22:   EOBL(population, fitness)
23:   UpdateBestSolution()
24:
25:   // Phase 2: Encircling
26:   θ ← 2π × t / I
27:   for i = 0 to P-1 do
28:      ApplyRotationMatrix(population[i], θ)
29:      fitness[i] ← Evaluate(population[i])
30:   end for
31:   UpdateBestSolution()
32:
33:   // EOBL #2
34:   EOBL(population, fitness)
35:   UpdateBestSolution()
36:
37:   // Phase 3: Attacking
38:   ω(t) ← ω_min + (ω_max - ω_min) × (1 - t/I)^k
39:   target ← SelectTarget(elitePool, bestSolution)
40:   for i = 0 to P-1 do
41:      population[i] ← ω(t) × population[i] + (1-ω(t)) × target
42:      fitness[i] ← Evaluate(population[i])
43:   end for
44:   UpdateBestSolution()
45:
46:   // Update Elite Pool
47:   UpdateElitePool(population, fitness, size=15)
48:
49:   // Record Convergence
50:   convergenceRecord[t] ← bestFitness
51:
52: end for
53:
54: return bestSolution, bestFitness
```

---

## Key Decision Points

### 1. **EOBL Boundary Adaptation**
- **Decision**: Use dynamic boundaries (αⱼ, βⱼ) based on current population
- **Alternative**: Fixed boundaries [0, 1]
- **Why**: Adapts to population distribution, better exploration

### 2. **Elite Pool Size**
- **Decision**: Top-15 solutions with logarithmic weighting
- **Alternative**: Top-5, Top-10, or Top-20
- **Why**: Balances diversity (15) vs. quality (top-only)

### 3. **Adaptive Elite Usage**
- **Decision**: 30% → 20% → 10% probability over iterations
- **Alternative**: Fixed 30% or always use elite
- **Why**: High exploration early, strong convergence late

### 4. **Dynamic Inertia k=3**
- **Decision**: Cubic decay (k=3)
- **Alternative**: Linear (k=1), Quadratic (k=2), Quartic (k=4)
- **Why**: Rapid mid-iteration transition (t=40-70) from exploration to exploitation

### 5. **EOBL Frequency**
- **Decision**: Twice per iteration (after Searching and Encircling)
- **Alternative**: Once per iteration, or every N iterations
- **Why**: Maximizes exploration opportunities while maintaining convergence

---

## Visual Flowchart Creation Guide

### Tools:
1. **draw.io** (Recommended): Free, web-based, export to PNG/SVG/PDF
2. **Microsoft Visio**: Professional diagrams, part of Office suite
3. **Lucidchart**: Online collaboration, academic licenses available
4. **PowerPoint/Keynote**: Simple flowcharts with SmartArt

### Recommended Layout:
```
┌────────────────────────────────────────┐
│          ICBO-Enhanced Main Flow       │
│  (Use this flowchart for the paper)   │
└────────────────────────────────────────┘

      [Phase-by-Phase Detailed Views]
            ↓                ↓
   ┌─────────────┐   ┌──────────────┐
   │ Searching + │   │ Encircling + │
   │   EOBL #1   │   │   EOBL #2    │
   └─────────────┘   └──────────────┘
            ↓                ↓
      ┌──────────────────────────┐
      │  Attacking + Elite Pool  │
      └──────────────────────────┘
```

### Color Scheme (Recommended):
- **Initialization**: Light Blue (#E3F2FD)
- **Phase 1 (Searching)**: Light Green (#E8F5E9)
- **EOBL**: Light Yellow (#FFF9C4)
- **Phase 2 (Encircling)**: Light Orange (#FFE0B2)
- **Phase 3 (Attacking)**: Light Red (#FFCDD2)
- **Elite Pool**: Light Purple (#F3E5F5)
- **Decisions**: Diamond shape, Light Gray (#EEEEEE)
- **Loop**: Dashed arrows, Dark Gray (#757575)

### Shape Guidelines:
- **Start/End**: Rounded rectangle (Terminator)
- **Process**: Rectangle
- **Decision**: Diamond
- **Input/Output**: Parallelogram
- **Loop**: Rectangle with curved bottom arrow back
- **Sub-process**: Rectangle with double-struck vertical edges

---

## Complexity Annotations for Flowchart

Add these complexity labels to the visual flowchart for academic rigor:

- **Initialization**: `O(P × M)`
- **Searching Phase**: `O(P × M)`
- **EOBL**: `O(P × M)` (boundary calc + opposition)
- **Encircling Phase**: `O(P × M)`
- **Attacking Phase**: `O(P × M)`
- **Elite Pool Update**: `O(P log P)`
- **Total Loop**: `O(I × P × M)` where I=100 iterations

---

## Export Settings for Paper

When exporting the flowchart for publication:

1. **Format**: PNG or PDF (vector preferred)
2. **Resolution**: 300 DPI minimum
3. **Size**: Single-column (3.5 inches) or double-column (7 inches) width
4. **Font**: Arial or Times New Roman, 10-12pt for labels
5. **Line Width**: 1.5-2pt for arrows, 2-3pt for main flow
6. **Margins**: 0.25 inches around diagram

---

## References for Flowchart

When citing components in the paper:

- **CBO Base**: Khatab et al. (2025) - Stages 1, 3, 5
- **Dynamic Inertia**: This work - k=3 cubic decay
- **ERTH Strategies**: Qin et al. (2024) - Bernoulli, EOBL, Elite Pool
- **Cloud Scheduling**: This work - Discrete mapping, CloudSim integration

---

**End of Flowchart Documentation**
