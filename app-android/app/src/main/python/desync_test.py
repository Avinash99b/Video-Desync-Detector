#!/usr/bin/env python3

import json
import sys
import tempfile
import os
import shutil
from pathlib import Path

import numpy as np
from scipy.io import wavfile
from scipy.signal import correlate
from java.lang import System

# -------------------------
# ✅ Progress helper
# -------------------------
def _emit_progress(progress_callback, stage, percent):
    if progress_callback:
        progress_callback(stage, int(percent))
    else:
        print(f"{stage}: {int(percent)}%")




# -------------------------
# ✅ WAV loader
# -------------------------
def load_wav(path):
    rate, data = wavfile.read(str(path))

    if data.ndim > 1:
        data = data[:, 0]

    data = data.astype(np.float32)

    peak = np.max(np.abs(data))
    if peak > 0:
        data /= peak

    return rate, data


# -------------------------
# ✅ Cross-correlation delay detection
# -------------------------
def xcorr_delay(ref, other, sr, progress_callback=None, max_delay_sec=30):
    _emit_progress(progress_callback, "Computing Delay", 60)

    ds = max(1, sr // 8000)
    r = ref[::ds]
    o = other[::ds]
    sr_ds = sr // ds

    n = min(len(r), len(o))
    r, o = r[:n], o[:n]

    win = min(n, int(max_delay_sec * sr_ds))

    corr = correlate(r, o, mode="full")

    _emit_progress(progress_callback, "Computing Delay", 85)

    mid = len(r) - 1
    lo = max(0, mid - win)
    hi = min(len(corr), mid + win + 1)

    sub_corr = corr[lo:hi]
    lag = (lo + np.argmax(np.abs(sub_corr))) - mid

    _emit_progress(progress_callback, "Computing Delay", 100)

    return (lag / sr_ds) * 1000.0  # ms


# -------------------------
# ✅ Main detection
# -------------------------
def run_detection(ref_wav_path, other_wav_path, progress_callback=None):

    try:
        # -------------------------
        # Load audio directly
        # -------------------------
        _emit_progress(progress_callback, "Loading Audio", 20)
        sr, ref_audio = load_wav(ref_wav_path)

        _emit_progress(progress_callback, "Loading Audio", 50)
        _, other_audio = load_wav(other_wav_path)

        # -------------------------
        # Compute delay
        # -------------------------
        _emit_progress(progress_callback, "Processing", 70)

        delay = xcorr_delay(
            ref_audio,
            other_audio,
            sr,
            progress_callback=progress_callback
        )

        _emit_progress(progress_callback, "Done", 100)

        return delay

    except Exception as e:
        System.out.println("----------Exception Occurred------------------")
        System.out.println(str(e))
        raise e