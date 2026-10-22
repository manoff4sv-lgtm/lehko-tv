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

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        scene = new FireScene(this);
        setContentView(scene.root);
    }

    @Override protected void onResume() {
        super.onResume();
        scene.start();
    }

    @Override protected void onPause() {
        scene.stop();
        super.onPause();
    }

    /** Volume keys still change the volume; every other key ends the fireplace (on release, so home doesn't get it). */
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.dispatchKeyEvent(e);
        }
        if (e.getAction() == KeyEvent.ACTION_UP && !isFinishing()) finish();
        return true;
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
