/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.util.rule;

import android.os.SystemClock;

import androidx.test.InstrumentationRegistry;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A rule that generates a file that helps diagnosing cases when the test process was terminated
 * because the test execution took too long, and tests that ran for too long even without being
 * terminated. If the process was terminated or the test was long, the test leaves an artifact with
 * stack traces of all threads, every SAMPLE_INTERVAL_MS. This will help understanding where we
 * stuck.
 */
public class SamplerRule implements TestRule {
    private static final int TOO_LONG_TEST_MS = 180000;
    private static final int SAMPLE_INTERVAL_MS = 3000;
    private static final int MAX_THREADS_WITH_SAME_NAME = 10;
    private static final AtomicBoolean sTooManyThreadsAssertionThrown = new AtomicBoolean(false);

    private static Thread startThread(Description description, UncaughtExceptionHandler handler) {
        Thread thread =
                new Thread() {
                    @Override
                    public void run() {
                        // Write all-threads stack stace every SAMPLE_INTERVAL_MS while the test
                        // is running.
                        // After the test finishes, delete that file. If the test process is
                        // terminated due to timeout, the trace file won't be deleted.
                        final File file = getFile();
                        boolean assertionThrown = false;

                        final long startTime = SystemClock.elapsedRealtime();
                        try (OutputStreamWriter outputStreamWriter =
                                     new OutputStreamWriter(
                                             new BufferedOutputStream(
                                                     new FileOutputStream(file)))) {
                            writeSamples(outputStreamWriter);
                        } catch (IOException | InterruptedException e) {
                            // Simply suppressing the exceptions, nothing to do here.
                        } catch (AssertionError e) {
                            assertionThrown = true;
                            throw e; // Rethrow to be caught by the UncaughtExceptionHandler.
                        } finally {
                            // If the process is not killed, then there was no test timeout, and
                            // we are not interested in the trace file, unless the test ran too
                            // long or an assertion regarding having too many threads with the same
                            // name was thrown.
                            if (!assertionThrown
                                    && SystemClock.elapsedRealtime() - startTime
                                    < TOO_LONG_TEST_MS) {
                                file.delete();
                            }
                        }
                    }

                    private File getFile() {
                        final String strDate = new SimpleDateFormat("HH:mm:ss").format(new Date());

                        final String descStr = description.getTestClass().getSimpleName() + "."
                                + description.getMethodName();
                        return artifactFile(
                                "ThreadStackSamples-" + strDate + "-" + descStr + ".txt");
                    }

                    private void writeSamples(OutputStreamWriter writer)
                            throws IOException, InterruptedException {
                        int count = 0;
                        while (true) {
                            writer.write(
                                    "Slice # "
                                    + (count++)
                                    + " @ "
                                    + new SimpleDateFormat("HH:mm:ss.SSS").format(new Date())
                                    + "\r\n");

                            final Map<Thread, StackTraceElement[]> allStackTraces =
                                    Thread.getAllStackTraces();

                            for (Map.Entry<Thread, StackTraceElement[]> entry :
                                    allStackTraces.entrySet()) {
                                writer.write("  Thread \"" + entry.getKey().getName()
                                        + "\"\r\n");
                                for (StackTraceElement frame : entry.getValue()) {
                                    writer.write("    " + frame.toString() + "\r\n");
                                }
                            }
                            writer.flush();

                            // Conditionally check for too many threads with the same name.
                            // This check is globally disabled after the first failure.
                            if (!sTooManyThreadsAssertionThrown.get()) {
                                // Count threads with the same name.
                                final Map<String, Integer> threadNameCounts = new HashMap<>();
                                for (Thread t : allStackTraces.keySet()) {
                                    threadNameCounts.compute(t.getName(),
                                            (k, v) -> (v == null) ? 1 : v + 1);
                                }

                                // Check if any thread name is repeated more than
                                // MAX_THREADS_WITH_SAME_NAME times.
                                for (Map.Entry<String, Integer> e : threadNameCounts.entrySet()) {
                                    if (e.getValue() > MAX_THREADS_WITH_SAME_NAME) {
                                        sTooManyThreadsAssertionThrown.set(true);
                                        final String errorMessage =
                                                "Assertion failed: More than "
                                                        + MAX_THREADS_WITH_SAME_NAME
                                                + " threads with the same name '" + e.getKey()
                                                + "'. Count: " + e.getValue();
                                        // Save the error in the samples file, after the
                                        // corresponding slice.
                                        writer.write(errorMessage + "\r\n");
                                        writer.flush();
                                        throw new AssertionError(errorMessage);
                                    }
                                }
                            }

                            sleep(SAMPLE_INTERVAL_MS);
                        }
                    }
                };

        thread.setUncaughtExceptionHandler(handler);
        thread.start();
        return thread;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                final Throwable[] samplerException = new Throwable[1];
                final UncaughtExceptionHandler handler = (thread, throwable) -> {
                    samplerException[0] = throwable;
                };

                final Thread traceThread = startThread(description, handler);
                try {
                    base.evaluate();
                } finally {
                    traceThread.interrupt();
                    traceThread.join();

                    // Rethrow exception from sampler thread, if any.
                    if (samplerException[0] != null) {
                        throw samplerException[0];
                    }
                }
            }
        };
    }

    private static File artifactFile(String fileName) {
        return new File(
                InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(),
                fileName);
    }
}
