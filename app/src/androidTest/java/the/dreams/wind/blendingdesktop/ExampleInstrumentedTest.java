package the.dreams.wind.blendingdesktop;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getTargetContext();

        assertEquals("the.dreams.wind.blendingdesktop", appContext.getPackageName());
    }

    // TODO: cover orientation change 1) translation matrix matches paddings
    // 2) only one overlay view at a time
    // 3) overlay is on the screen after screen rotation
}
