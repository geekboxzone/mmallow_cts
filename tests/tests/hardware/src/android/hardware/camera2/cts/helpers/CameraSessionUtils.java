/*
 * Copyright 2014 The Android Open Source Project
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
package android.hardware.camera2.cts.helpers;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.cts.CameraTestUtils;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;

import com.android.ex.camera2.blocking.BlockingCaptureListener;
import com.android.ex.camera2.blocking.BlockingSessionListener;
import com.android.ex.camera2.exceptions.TimeoutRuntimeException;

import junit.framework.Assert;

import org.mockito.internal.util.MockUtil;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import static com.android.internal.util.Preconditions.*;
import static org.mockito.Mockito.*;

/**
 * A utility class with common functions setting up sessions and capturing.
 */
public class CameraSessionUtils extends Assert {
    private static final String TAG = "CameraSessionUtils";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    /**
     * A blocking listener class for synchronously opening and configuring sessions.
     */
    public static class SessionListener extends BlockingSessionListener {
        private final LinkedBlockingQueue<CameraCaptureSession> mSessionQueue =
                new LinkedBlockingQueue<>();

        /**
         * Get a new configured {@link CameraCaptureSession}.
         *
         * <p>
         * This method is blocking, and will time out after
         * {@link CameraTestUtils#SESSION_CONFIGURE_TIMEOUT_MS}.
         * </p>
         *
         * @param device the {@link CameraDevice} to open a session for.
         * @param outputs the {@link Surface} outputs to configure.
         * @param handler the {@link Handler} to use for callbacks.
         * @return a configured {@link CameraCaptureSession}.
         *
         * @throws CameraAccessException if any of the {@link CameraDevice} methods fail.
         * @throws TimeoutRuntimeException if no result was received before the timeout.
         */
        public synchronized CameraCaptureSession getConfiguredSession(CameraDevice device,
                                                                      List<Surface> outputs,
                                                                      Handler handler)
                throws CameraAccessException {
            device.createCaptureSession(outputs, this, handler);
            getStateWaiter().waitForState(SESSION_CONFIGURED,
                    CameraTestUtils.SESSION_CONFIGURE_TIMEOUT_MS);
            return mSessionQueue.poll();
        }

        @Override
        public void onConfigured(CameraCaptureSession session) {
            mSessionQueue.offer(session);
            super.onConfigured(session);
        }
    }

    /**
     * A blocking listener class for synchronously capturing and results with a session.
     */
    public static class CaptureListener extends BlockingCaptureListener {
        private final LinkedBlockingQueue<TotalCaptureResult> mResultQueue =
                new LinkedBlockingQueue<>();
        private final LinkedBlockingQueue<Long> mCaptureTimeQueue =
                new LinkedBlockingQueue<>();

        /**
         * Capture a new result with the given {@link CameraCaptureSession}.
         *
         * <p>
         * This method is blocking, and will time out after
         * {@link CameraTestUtils#CAPTURE_RESULT_TIMEOUT_MS}.
         * </p>
         *
         * @param session the {@link CameraCaptureSession} to use.
         * @param request the {@link CaptureRequest} to capture with.
         * @param handler the {@link Handler} to use for callbacks.
         * @return a {@link Pair} containing the capture result and capture time.
         *
         * @throws CameraAccessException if any of the {@link CameraDevice} methods fail.
         * @throws TimeoutRuntimeException if no result was received before the timeout.
         */
        public synchronized Pair<TotalCaptureResult, Long> getCapturedResult(
                CameraCaptureSession session, CaptureRequest request, Handler handler)
                throws CameraAccessException {
            session.capture(request, this, handler);
            getStateWaiter().waitForState(CAPTURE_COMPLETED,
                    CameraTestUtils.CAPTURE_RESULT_TIMEOUT_MS);
            return new Pair<>(mResultQueue.poll(), mCaptureTimeQueue.poll());
        }

        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request,
                                     long timestamp) {
            mCaptureTimeQueue.offer(timestamp);
            super.onCaptureStarted(session, request, timestamp);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                       TotalCaptureResult result) {
            mResultQueue.offer(result);
            super.onCaptureCompleted(session, request, result);
        }
    }

    /**
     * Get a mocked {@link CaptureListener}.
     */
    public static CaptureListener getMockCaptureListener() {
        return spy(new CaptureListener());
    }

    /**
     * Get a mocked {@link CaptureListener}.
     */
    public static SessionListener getMockSessionListener() {
        return spy(new SessionListener());
    }

    /**
     * Configure and return a new {@link CameraCaptureSession}.
     *
     * <p>
     * This will verify that the correct session callbacks are called if a mocked listener is
     * passed as the {@code listener} argument. This method is blocking, and will time out after
     * {@link CameraTestUtils#SESSION_CONFIGURE_TIMEOUT_MS}.
     * </p>
     *
     * @param listener a {@link SessionListener} to use for callbacks.
     * @param device the {@link CameraDevice} to use.
     * @param outputs the {@link Surface} outputs to configure.
     * @param handler the {@link Handler} to call callbacks on.
     * @return a configured {@link CameraCaptureSession}.
     *
     * @throws CameraAccessException if any of the {@link CameraDevice} methods fail.
     * @throws TimeoutRuntimeException if no result was received before the timeout.
     */
    public static CameraCaptureSession configureAndVerifySession(SessionListener listener,
                                                                 CameraDevice device,
                                                                 List<Surface> outputs,
                                                                 Handler handler)
            throws CameraAccessException {
        checkNotNull(listener);
        checkNotNull(device);
        checkNotNull(handler);
        checkCollectionNotEmpty(outputs, "outputs");
        checkCollectionElementsNotNull(outputs, "outputs");

        CameraCaptureSession session = listener.getConfiguredSession(device, outputs, handler);
        if (new MockUtil().isMock(listener)) {
            verify(listener, never()).onConfigureFailed(any(CameraCaptureSession.class));
            verify(listener, never()).onClosed(eq(session));
            verify(listener, atLeastOnce()).onConfigured(eq(session));
        }

        checkNotNull(session);
        return session;
    }

    /**
     * Capture and return a new {@link TotalCaptureResult}.
     *
     * <p>
     * This will verify that the correct capture callbacks are called if a mocked listener is
     * passed as the {@code listener} argument. This method is blocking, and will time out after
     * {@link CameraTestUtils#CAPTURE_RESULT_TIMEOUT_MS}.
     * </p>
     *
     * @param listener a {@link CaptureListener} to use for callbacks.
     * @param session the {@link CameraCaptureSession} to use.
     * @param request the {@link CaptureRequest} to capture with.
     * @param handler the {@link Handler} to call callbacks on.
     * @return a {@link Pair} containing the capture result and capture time.
     *
     * @throws CameraAccessException if any of the {@link CameraDevice} methods fail.
     * @throws TimeoutRuntimeException if no result was received before the timeout.
     */
    public static Pair<TotalCaptureResult, Long> captureAndVerifyResult(CaptureListener listener,
            CameraCaptureSession session, CaptureRequest request, Handler handler)
            throws CameraAccessException {
        checkNotNull(listener);
        checkNotNull(session);
        checkNotNull(request);
        checkNotNull(handler);

        Pair<TotalCaptureResult, Long> result = listener.getCapturedResult(session, request,
                handler);
        if (new MockUtil().isMock(listener)) {
            verify(listener, never()).onCaptureFailed(any(CameraCaptureSession.class),
                    any(CaptureRequest.class), any(CaptureFailure.class));
            verify(listener, atLeastOnce()).onCaptureStarted(eq(session), eq(request), anyLong());
            verify(listener, atLeastOnce()).onCaptureCompleted(eq(session), eq(request),
                    eq(result.first));
        }

        checkNotNull(result);
        return result;
    }

    // Suppress default constructor for noninstantiability
    private CameraSessionUtils() { throw new AssertionError(); }
}
