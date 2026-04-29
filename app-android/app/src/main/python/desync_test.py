#!/usr/bin/env python3

import json
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
from scipy.io import wavfile
from scipy.signal import correlate


def _emit_progress(progress_callback, stage, percent):
    if progress_callback:
        progress_callback(stage, int(percent))
    else:
        print(f"Progress: {stage}: {int(percent)}%")


def extract_audio(video_path, stream_index, output_path):
    cmd = [
        "ffmpeg", "-y", "-v", "quiet",
        "-i", str(video_path),
        "-map", f"0:{stream_index}",
        "-ac", "1",
        "-ar", "16000",
        "-f", "wav",
        str(output_path),
    ]
    subprocess.run(cmd, check=True)


def load_wav(path):
    rate, data = wavfile.read(str(path))
    if data.ndim > 1:
        data = data[:, 0]
    data = data.astype(np.float32)
    peak = np.max(np.abs(data))
    if peak > 0:
        data /= peak
    return rate, data


def get_audio_tracks(video_path):
    cmd = [
        "ffprobe", "-v", "quiet",
        "-print_format", "json",
        "-show_streams",
        str(video_path),
    ]
    result = subprocess.run(cmd, capture_output=True, text=True, check=True)
    info = json.loads(result.stdout)
    return [s for s in info["streams"] if s["codec_type"] == "audio"]


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
    return (lag / sr_ds) * 1000.0


def run_detection(file_path, progress_callback=None):
    video = Path(file_path)
    if not video.exists():
        raise FileNotFoundError(f"Input file not found: {video}")

    _emit_progress(progress_callback, "Parsing Input", 5)
    tracks = get_audio_tracks(video)

    if len(tracks) < 2:
        raise ValueError("Need at least 2 audio tracks")

    ref_idx = tracks[0]["index"]
    other_idx = tracks[1]["index"]

    with tempfile.TemporaryDirectory() as tmp:
        ref_wav = Path(tmp) / "ref.wav"
        other_wav = Path(tmp) / "other.wav"

        _emit_progress(progress_callback, "Extracting Audio", 10)
        extract_audio(video, ref_idx, ref_wav)

        _emit_progress(progress_callback, "Extracting Audio", 40)
        extract_audio(video, other_idx, other_wav)

        _emit_progress(progress_callback, "Extracting Audio", 70)
        sr, ref_audio = load_wav(ref_wav)

        _emit_progress(progress_callback, "Extracting Audio", 85)
        _, other_audio = load_wav(other_wav)

        _emit_progress(progress_callback, "Extracting Audio", 100)

        return xcorr_delay(ref_audio, other_audio, sr, progress_callback=progress_callback)


def main():
    if len(sys.argv) < 2:
        print("Usage: python script.py <video>")
        sys.exit(1)

    delay_ms = run_detection(sys.argv[1])
    if abs(delay_ms) < 50:
        print("No Desync Found")
    else:
        print(f"Desync Detected: {delay_ms:+.1f} ms")


if __name__ == "__main__":
    main()
