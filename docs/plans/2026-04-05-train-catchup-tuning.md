# Train Catch-Up Tuning Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Slightly increase long-distance follower catch-up strength without changing unlink distance or rail-alignment behavior.

**Architecture:** Keep the current distance-based boost formula and only tune the existing constants that control when catch-up starts, how strongly it ramps, and its cap. Preserve all other train-following behavior.

**Tech Stack:** Java 17, Fabric Loom, JUnit 5

---

### Task 1: Tighten the regression tests

**Files:**
- Modify: `src/test/java/better/furnace/minecart/BetterFurnaceTrainManagerTest.java`

**Step 1: Write the failing test**

Adjust the existing catch-up assertions so they require a slightly stronger long-distance boost than the current constants provide.

**Step 2: Run test to verify it fails**

Run: `.\gradlew.bat test --tests better.furnace.minecart.BetterFurnaceTrainManagerTest`
Expected: FAIL because the current catch-up constants are too weak for the tightened assertions.

**Step 3: Write minimal implementation**

Tune only:
- `FOLLOW_BOOST_START_DISTANCE`
- `FOLLOW_BOOST_PER_BLOCK`
- `MAX_FOLLOW_BOOST`

**Step 4: Run test to verify it passes**

Run: `.\gradlew.bat test --tests better.furnace.minecart.BetterFurnaceTrainManagerTest`
Expected: PASS

### Task 2: Verify full mod build

**Files:**
- Modify: `src/main/java/better/furnace/minecart/BetterFurnaceTrainManager.java`
- Test: `src/test/java/better/furnace/minecart/BetterFurnaceTrainManagerTest.java`

**Step 1: Run full build**

Run: `.\gradlew.bat build`
Expected: `BUILD SUCCESSFUL`
