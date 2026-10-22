package ua.lehko.tv;

import android.service.dreams.DreamService;

/** The fireplace as the TV's screensaver. */
public final class FireDream extends DreamService {
    private FireScene scene;

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        scene = new FireScene(this);
        setContentView(scene.root);
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        if (scene != null) scene.start();
    }

    @Override public void onDreamingStopped() {
        if (scene != null) scene.stop();
        super.onDreamingStopped();
    }

    @Override public void onDetachedFromWindow() {
        if (scene != null) scene.stop();
        scene = null;
        super.onDetachedFromWindow();
    }
}
