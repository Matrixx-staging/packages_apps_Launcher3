/*
 * Copyright (C) 2025 The Android Open Source Project
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

import android.util.Log;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.concurrent.locks.ReentrantLock;

/** Enforces a mutual exclusion to prevent flakiness from overlapping remote test runs. */
public class MutualExclusionRule implements TestRule {

    private static final String TAG = "MutualExclusionRule";

    private static final ReentrantLock MUTEX = new ReentrantLock();

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    Log.d(TAG, "In try-block: Enabling reentrant lock");
                    MUTEX.lock();
                    base.evaluate();
                } catch (Exception e) {
                    Log.e(TAG, "Error", e);
                    throw e;
                } finally {
                    Log.d(TAG, "In finally-block: Disabling reentrant lock");
                    MUTEX.unlock();
                }
            }
        };
    }
}
