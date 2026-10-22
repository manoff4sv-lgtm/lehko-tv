package ua.lehko.tv;

import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.text.TextUtils;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** The TV's HDMI (and other) inputs and the antenna/cable tuner, opened through the TV's own Live TV app. */
final class Inputs {
    private Inputs() {}

    static final class Input {
        final String id;
        final String label;
        final boolean tuner;

        Input(String id, String label, boolean tuner) {
            this.id = id;
            this.label = label;
            this.tuner = tuner;
        }
    }

    /** Physical inputs (HDMI, AV...) in the TV's order, then one entry for the tuner if there is one. */
    static List<Input> load(Context c) {
        List<Input> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        Input tuner = null;
        List<TvInputInfo> list;
        try {
            list = tim.getTvInputList();
        } catch (RuntimeException e) {
            return out;
        }
        for (TvInputInfo info : list) {
            try {
                if (info.isHidden(c) || info.getParentId() != null) continue;
                CharSequence custom = info.loadCustomLabel(c);
                CharSequence label = !TextUtils.isEmpty(custom) ? custom : info.loadLabel(c);
                if (info.isPassthroughInput()) {
                    if (info.getType() == TvInputInfo.TYPE_HDMI || info.getType() == TvInputInfo.TYPE_COMPONENT
                            || info.getType() == TvInputInfo.TYPE_COMPOSITE || info.getType() == TvInputInfo.TYPE_VGA
                            || info.getType() == TvInputInfo.TYPE_DVI) {
                        out.add(new Input(info.getId(), label == null ? "HDMI" : label.toString(), false));
                    }
                } else if (tuner == null && info.getType() == TvInputInfo.TYPE_TUNER) {
                    tuner = new Input(info.getId(), c.getString(R.string.live_tv), true);
                }
            } catch (RuntimeException ignored) {
                // A broken input service shouldn't take the home screen down.
            }
        }
        if (tuner != null) out.add(tuner);
        return out;
    }

    static void open(Context c, Input in) {
        Intent i = new Intent(Intent.ACTION_VIEW, in.tuner
                ? TvContract.buildChannelsUriForInput(in.id)
                : TvContract.buildChannelUriForPassthroughInput(in.id))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Apps.start(c, i)) return;
        if (in.tuner && Apps.start(c, new Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
            return;
        }
        Toast.makeText(c, R.string.cannot_open, Toast.LENGTH_SHORT).show();
    }
}
