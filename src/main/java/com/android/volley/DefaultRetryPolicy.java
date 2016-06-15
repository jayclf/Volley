/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.volley;

/**
 * Request的默认重试策略.
 */
public class DefaultRetryPolicy implements RetryPolicy {
    /** 当前的超时毫秒数. */
    private int mCurrentTimeoutMs;

    /** 当前的重试次数. */
    private int mCurrentRetryCount;

    /** 最大重试次数. */
    private final int mMaxNumRetries;

    /** 策略乘法器. */
    private final float mBackoffMultiplier;

    /** 默认的Socket超时毫秒数 */
    public static final int DEFAULT_TIMEOUT_MS = 2500;

    /** 默认的重试次数，不重试 */
    public static final int DEFAULT_MAX_RETRIES = 0;

    /** 默认的乘法器 */
    public static final float DEFAULT_BACKOFF_MULT = 1f;


    /**
     * 使用默认的超时时间构造一个实例.
     */
    public DefaultRetryPolicy() {
        this(DEFAULT_TIMEOUT_MS, DEFAULT_MAX_RETRIES, DEFAULT_BACKOFF_MULT);
    }

    /**
     * 构造一个新的重试策略.
     * @param initialTimeoutMs 初始化超时时间.
     * @param maxNumRetries 最大的重试次数.
     * @param backoffMultiplier 乘法器.
     */
    public DefaultRetryPolicy(int initialTimeoutMs, int maxNumRetries, float backoffMultiplier) {
        mCurrentTimeoutMs = initialTimeoutMs;
        mMaxNumRetries = maxNumRetries;
        mBackoffMultiplier = backoffMultiplier;
    }

    /**
     * 返回当前超时时间.
     */
    @Override
    public int getCurrentTimeout() {
        return mCurrentTimeoutMs;
    }

    /**
     * 返回当前重试次数.
     */
    @Override
    public int getCurrentRetryCount() {
        return mCurrentRetryCount;
    }

    /**
     * 返回后退乘法器.
     */
    public float getBackoffMultiplier() {
        return mBackoffMultiplier;
    }

    /**
     * 准备下一次重试.
     * @param error 上次重试的返回码.
     */
    @Override
    public void retry(VolleyError error) throws VolleyError {
        mCurrentRetryCount++;
        mCurrentTimeoutMs += (mCurrentTimeoutMs * mBackoffMultiplier);
        if (!hasAttemptRemaining()) {
            throw error;
        }
    }

    /**
     * 返回还没有没有重试次数.
     */
    protected boolean hasAttemptRemaining() {
        return mCurrentRetryCount <= mMaxNumRetries;
    }
}
