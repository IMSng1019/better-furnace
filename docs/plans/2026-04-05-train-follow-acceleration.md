# Train Follow Acceleration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Restore vanilla drag feel by changing train following from direct velocity replacement to bounded acceleration-style correction.

**Architecture:** Keep the current follower target-velocity calculation, then convert the gap between current motion and target motion into a bounded horizontal adjustment. Apply that adjustment on top of the current motion instead of replacing the full horizontal velocity.

**Tech Stack:** Java 17, Fabric Loom, JUnit 5

---

### Task 1: Add failing regression tests

**Files:**
- Modify: `src/test/java/better/furnace/minecart/BetterFurnaceTrainManagerTest.java`

**Step 1: Write the failing test**

Add tests for a new helper that:
- returns zero when current and target match
- returns only the delta needed when the difference is small
- caps the adjustment when the difference is large

**Step 2: Run test to verify it fails**

Run: `.\gradlew.bat test --tests better.furnace.minecart.BetterFurnaceTrainManagerTest`
Expected: FAIL because the bounded follow-adjustment helper does not exist yet.

**Step 3: Write minimal implementation**

Modify `src/main/java/better/furnace/minecart/BetterFurnaceTrainManager.java` to:
- add a bounded horizontal follow-adjustment helper
- use `current + adjustment` rather than directly writing the computed target horizontal velocity

Do not change unlink thresholds or the existing geometry tolerance logic.

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
