# Train Follow Acceleration Design

**Problem**

The current follower logic computes a target horizontal velocity and directly overwrites the follower minecart's horizontal motion each tick.

That preserves train spacing, but it also largely defeats vanilla drag and resistance because any slowdown applied by the base game is re-covered on the next tick by another direct velocity assignment.

**Chosen Approach**

Keep the current target-velocity computation, but stop writing that velocity directly into the follower.

Instead:
- compute the target horizontal motion exactly as before
- derive a bounded horizontal adjustment from the difference between current motion and target motion
- apply only that bounded adjustment before the vanilla tick continues

This turns follower control from a velocity override into a traction/acceleration input. Vanilla slowdown, collisions, and other resistance effects remain part of the outcome.

**Why This Approach**

- Restores the feel of vanilla drag without removing train following
- Minimizes changes to the existing train logic
- Preserves the current unlink threshold and current geometry tolerance changes
- Avoids redesigning the minecart tick order or rewriting vanilla slowdown

**Validation**

- Add regression tests proving the helper returns an adjustment rather than a replacement velocity
- Verify zero adjustment when current and target already match
- Run targeted train-manager tests and the full build
