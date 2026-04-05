# Train Dynamic Tolerance Design

**Problem**

The current unlink logic uses a fixed straight-line threshold between adjacent minecarts. On sharp geometry changes, especially a turn immediately followed by an ascent, the tail cart can temporarily drift farther from the previous cart and unlink even though the train is still negotiating the same local track feature.

The goal is not to redesign following or add unlink delay. The goal is to make adjacent-link validation slightly more tolerant in the specific moments where track geometry changes abruptly.

**Chosen Approach**

Keep adjacent unlinking and the existing follow/catch-up logic. Add a small dynamic tolerance bonus to the unlink threshold based on the local geometry near the previous cart.

The bonus will come from two local signals:
- **turn severity** derived from the previous cart's recent track-history tangents
- **vertical gap** between the previous and current cart, which captures immediate climb transitions

When the local path is straight and flat, the bonus stays zero and unlink behavior is unchanged. When the previous cart is just coming out of a bend or onto a slope, the allowed adjacent distance is slightly increased for that pair only.

**Why This Approach**

- Preserves the current chain structure and current catch-up tuning
- Avoids delayed unlink logic, which was explicitly rejected
- Targets the exact geometry where false unlinks happen
- Keeps the change local to adjacent validation rather than redesigning train references

**Validation**

- Add tests proving the geometry bonus is zero on straight history and positive on turning history
- Add tests proving the allowed unlink distance increases when turn severity or vertical gap exists
- Run targeted train-manager tests and the full build
