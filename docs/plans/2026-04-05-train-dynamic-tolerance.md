# Train Dynamic Tolerance Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add small geometry-aware tolerance to adjacent minecart unlinking so curve-to-slope transitions are less likely to break the tail cart link.

**Architecture:** Keep the current adjacent link model and fixed base threshold, then add a bounded local bonus derived from recent history turn severity and current vertical gap. Apply that bonus only inside link validation.

**Tech Stack:** Java 17, Fabric Loom, JUnit 5

---

### Task 1: Add failing regression tests

**Files:**
- Modify: `src/test/java/better/furnace/minecart/BetterFurnaceTrainManagerTest.java`

**Step 1: Write the failing test**

Add tests for:
- zero geometry bonus on straight history
- positive geometry bonus on turning history
- higher allowed distance when geometry bonus is present

**Step 2: Run test to verify it fails**

Run: `.\gradlew.bat test --tests better.furnace.minecart.BetterFurnaceTrainManagerTest`
Expected: FAIL because the geometry-tolerance helpers do not exist yet.

**Step 3: Write minimal implementation**

Modify `src/main/java/better/furnace/minecart/BetterFurnaceTrainManager.java` to:
- derive turn severity from recent history tangents
- compute a bounded local tolerance bonus
- use the adjusted threshold in adjacent unlink validation

Do not change follow spacing, catch-up boost, or unlink delay behavior.

**Step 4: Run test to verify it passes**

Run: `.\gradlew.bat test --tests better.furnace.minecart.BetterFurnaceTrainManagerTest`
Expected: PASS

### Task 2: Verify full build

**Files:**
- Modify: `src/main/java/better/furnace/minecart/BetterFurnaceTrainManager.java`
- Test: `src/test/java/better/furnace/minecart/BetterFurnaceTrainManagerTest.java`

**Step 1: Run full build**

Run: `.\gradlew.bat build`
Expected: `BUILD SUCCESSFUL`
