package ua.lehko.tv;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;

/**
 * The fireplace after a few idle minutes on the home screen. Any key goes back home. The screen is not kept on: the
 * TV's own screensaver and sleep timers still work (and stop the fire with this screen).
 */
public final class FireplaceActivity extends Activity {
    private FireScene scene;
    private boolean resumed;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        scene = new FireScene(this, true);
        setContentView(scene.root);
        // The scene covers the whole window: no window background to paint under it every frame.
        getWindow().setBackgroundDrawable(null);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        scene.start();
    }

    @Override protected void onPause() {
        resumed = false;
        scene.stop();
        super.onPause();
    }

    /**
     * Before Android 11 the TV's own screensaver is a window over this one, which stays resumed: the fire (and the
     * crackle) stop while something else has the focus.
     */
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && resumed) scene.start();
        else if (!hasFocus) scene.stop();
    }

    /**
     * Volume and playback keys work as usual and the fire stays (music may be playing under it); Search still opens
     * the assistant; every other key ends the fireplace (on release, so home doesn't get it).
     */
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_STOP:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_HEADSETHOOK:
                // Not handled here, so the window hands them to the playing app's media session.
                return super.dispatchKeyEvent(e);
            case KeyEvent.KEYCODE_SEARCH: {
                // Press and release both go on: the window opens the assistant on a release it saw pressed.
                boolean handled = super.dispatchKeyEvent(e);
                if (e.getAction() == KeyEvent.ACTION_UP && !isFinishing()) finish();
                return handled;
            }
            default:
                if (e.getAction() == KeyEvent.ACTION_UP && !isFinishing()) finish();
                return true;
        }
    }

    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP && !isFinishing()) finish();
        return true;
    }

    @Override public void finish() {
        super.finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }
}
