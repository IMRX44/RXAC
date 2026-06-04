"""Pydantic request/response models for the API."""

from __future__ import annotations

from typing import Dict, List, Optional

from pydantic import BaseModel, Field


class Event(BaseModel):
    uuid: str
    name: str
    protocol: int = -1
    ts: int
    type: str = "feature"          # "feature" | "violation"
    check: Optional[str] = None
    category: Optional[str] = None
    vl: Optional[float] = None
    debug: Optional[str] = ""
    features: Dict[str, float] = Field(default_factory=dict)


class IngestRequest(BaseModel):
    events: List[Event]


class Verdict(BaseModel):
    uuid: str
    name: Optional[str] = None
    anomaly: float


class IngestResponse(BaseModel):
    received: int
    scored: int
    flagged: int
    verdicts: List[Verdict] = Field(default_factory=list)


class TrainResponse(BaseModel):
    trained: bool
    detail: str
    samples: int = 0
