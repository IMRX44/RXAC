"""Feature engineering shared between training and inference.

The plugin sends a compact per-event feature dict. We map it to a fixed-order
numeric vector so the model sees identical features at train and inference time.
"""

from __future__ import annotations

from typing import Dict, List

# Canonical feature order. NEVER reorder without retraining the model.
FEATURE_ORDER: List[str] = [
    "hSpeed",      # horizontal speed (blocks/tick)
    "dY",          # vertical delta (blocks/tick)
    "yawDelta",    # |yaw change| this tick
    "pitchDelta",  # |pitch change| this tick
    "airTicks",    # consecutive ticks airborne
    "cps",         # clicks in the last second
    "totalVl",     # summed deterministic violation level
]


def to_vector(features: Dict[str, float]) -> List[float]:
    """Map an event's feature dict to the canonical numeric vector."""
    return [float(features.get(name, 0.0) or 0.0) for name in FEATURE_ORDER]
