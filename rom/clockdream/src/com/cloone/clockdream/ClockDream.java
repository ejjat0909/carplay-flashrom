package com.cloone.clockdream;

import android.service.dreams.DreamService;

/** The system screen saver. A touch ends it, like any screen saver. */
public class ClockDream extends DreamService {

    private ClockView view;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        view = new ClockView(this);
        setContentView(view);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (view != null) {
            view.start();
        }
    }

    @Override
    public void onDreamingStopped() {
        if (view != null) {
            view.stop();
        }
        super.onDreamingStopped();
    }
}
