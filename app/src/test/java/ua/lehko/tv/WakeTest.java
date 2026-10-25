package ua.lehko.tv;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** What the helper opens when the TV is turned on. */
public class WakeTest {
    private static final Inputs.Input HDMI2 = new Inputs.Input("com.droidlogic.tvinput/.services.Hdmi2InputService/HW6",
            "Xbox", "2", false);
    private static final Inputs.Input TUNER = new Inputs.Input("com.droidlogic.tvinput/.services.ADTVInputService/HW16",
            "Live TV", "", true);
    private static final List<Inputs.Input> INPUTS = Arrays.asList(HDMI2, TUNER);

    @Test public void theChosenInputWhileTheTvHasIt() {
        assertSame(HDMI2, Keys.wakeInput(Keys.WAKE_INPUT + HDMI2.id, INPUTS));
        assertSame(TUNER, Keys.wakeInput(Keys.WAKE_INPUT + TUNER.id, INPUTS));
        assertNull("gone since", Keys.wakeInput(Keys.WAKE_INPUT + "com.example/.Hdmi/HW9", INPUTS));
    }

    @Test public void theHomeScreenOpensNothing() {
        assertNull(Keys.wakeInput(Keys.WAKE_HOME, INPUTS));
        assertNull("nothing chosen", Keys.wakeInput(null, INPUTS));
        assertNull("not ours", Keys.wakeInput(HDMI2.id, INPUTS));
    }
}
