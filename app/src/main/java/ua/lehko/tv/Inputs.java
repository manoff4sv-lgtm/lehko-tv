package ua.lehko.tv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ServiceInfo;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.text.TextUtils;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The TV's HDMI (and other) inputs and the antenna/cable tuner, opened through the TV's own Live TV app. */
final class Inputs {
    private Inputs() {}

    static final class Input {
        final String id;
        final String label;
        /** The port number from the TV's own name ("HDMI 2" gives "2"), whatever the input was renamed to. */
        final String badge;
        final boolean tuner;

        Input(String id, String label, String badge, boolean tuner) {
            this.id = id;
            this.label = label;
            this.badge = badge;
            this.tuner = tuner;
        }
    }

    /**
     * Physical inputs, HDMI first, each kind by the TV's own name (the system's list comes in hash order: 3, AV, 2, 1),
     * then one entry for the tuner if there is one.
     */
    static List<Input> load(Context c) {
        List<Input> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        Input tuner = null;
        int tunerScore = -1;
        // The TV's own names sort the inputs, so renaming one doesn't move its button.
        final Map<Input, String> order = new HashMap<>();
        List<TvInputInfo> list;
        try {
            list = tim.getTvInputList();
        } catch (RuntimeException e) {
            return out;
        }
        for (TvInputInfo info : list) {
            try {
                if (info.isHidden(c) || info.getParentId() != null) continue;
                CharSequence own = info.loadLabel(c);
                CharSequence custom = info.loadCustomLabel(c);
                CharSequence label = !TextUtils.isEmpty(custom) ? custom : own;
                int type = info.getType();
                if (info.isPassthroughInput()) {
                    if (type == TvInputInfo.TYPE_HDMI || type == TvInputInfo.TYPE_COMPONENT
                            || type == TvInputInfo.TYPE_COMPOSITE || type == TvInputInfo.TYPE_VGA
                            || type == TvInputInfo.TYPE_DVI) {
                        Input in = new Input(info.getId(), label == null ? "HDMI" : label.toString(),
                                own == null ? "" : IconButton.badgeOf(own.toString()), false);
                        order.put(in, (type == TvInputInfo.TYPE_HDMI ? "0" : "1") + (own == null ? "" : own));
                        out.add(in);
                    }
                } else if (type == TvInputInfo.TYPE_TUNER) {
                    // Apps that feed channels to Live TV are tuner inputs too: the TV's own tuner (a system app's
                    // hardware input, "<service>/HW<n>") wins over them.
                    ServiceInfo si = info.getServiceInfo();
                    boolean system = si != null && si.applicationInfo != null
                            && (si.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    int score = (system ? 2 : 0) + (info.getId().contains("/HW") ? 1 : 0);
                    if (score > tunerScore) {
                        tuner = new Input(info.getId(), c.getString(R.string.live_tv), "", true);
                        tunerScore = score;
                    }
                }
            } catch (RuntimeException ignored) {
                // A broken input service shouldn't take the home screen down.
            }
        }
        Collections.sort(out, new Comparator<Input>() {
            @Override public int compare(Input a, Input b) {
                int r = order.get(a).compareToIgnoreCase(order.get(b));
                return r != 0 ? r : a.id.compareTo(b.id);
            }
        });
        if (tuner != null) out.add(tuner);
        return out;
    }

    /**
     * The input with this id (from {@link #load}) if the TV still has it, else null: enough to {@link #open} it. One
     * call to the system, for the helper, which needs no labels.
     */
    static Input byId(Context c, String id) {
        try {
            TvInputManager tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
            TvInputInfo info = tim == null ? null : tim.getTvInputInfo(id);
            return info == null ? null : new Input(id, "", "", !info.isPassthroughInput());
        } catch (RuntimeException e) {
            return null;
        }
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
