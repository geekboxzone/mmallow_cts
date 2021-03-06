/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.view.cts;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener;

public class ScaleGestureDetectorTest extends
        ActivityInstrumentationTestCase2<ScaleGestureDetectorCtsActivity> {

    private ScaleGestureDetector mScaleGestureDetector;
    private ScaleGestureDetectorCtsActivity mActivity;
    private Context mContext;

    public ScaleGestureDetectorTest() {
        super("com.android.cts.view", ScaleGestureDetectorCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
        mScaleGestureDetector = mActivity.getScaleGestureDetector();
        mContext = getInstrumentation().getTargetContext();
    }

    @UiThreadTest
    public void testConstructor() {
        new ScaleGestureDetector(
                mContext, new SimpleOnScaleGestureListener(), new Handler(Looper.getMainLooper()));
        new ScaleGestureDetector(mContext, new SimpleOnScaleGestureListener());
    }

    public void testAccessStylusScaleEnabled() {
        assertTrue(mScaleGestureDetector.isStylusScaleEnabled());
        mScaleGestureDetector.setStylusScaleEnabled(true);

        mScaleGestureDetector.setStylusScaleEnabled(false);
        assertFalse(mScaleGestureDetector.isStylusScaleEnabled());
    }
}