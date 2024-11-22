package com.limelight.binding.video;

public interface PerfOverlayListener {
    void onPerfUpdate(final int width, final int height, final float totalFps, final float receivedFps, final float renderedFps, final String decoder, final long rttInfo, final VideoStats lastTwo);
}
