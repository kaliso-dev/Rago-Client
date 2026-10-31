"""Token buckets: each key may make `rate` requests a minute, with bursts of up to `burst`."""

import threading
import time


class Limiter:
    def __init__(self, clock=time.monotonic):
        self._clock = clock
        self._buckets = {}
        self._lock = threading.Lock()
        self._swept = clock()

    def allow(self, name, key, rate, burst):
        now = self._clock()
        with self._lock:
            if now - self._swept > 300:
                self._sweep(now)
            tokens, last = self._buckets.get((name, key), (burst, now))
            tokens = min(burst, tokens + (now - last) * rate / 60.0)
            if tokens < 1:
                self._buckets[(name, key)] = (tokens, now)
                return False
            self._buckets[(name, key)] = (tokens - 1, now)
            return True

    def _sweep(self, now):
        # a bucket untouched for 10 minutes is full again: forget it
        self._buckets = {k: v for k, v in self._buckets.items() if now - v[1] < 600}
        self._swept = now
