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

package com.android.volley.toolbox;

import android.os.Handler;
import android.os.Looper;

import com.android.volley.Cache;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;

/**
 * 用于清除cache的综合请求.
 * 似乎没什么用
 */
public class ClearCacheRequest extends Request<Object> {
    private final Cache mCache;
    private final Runnable mCallback;

    /**
     * 创建一个用于清除cache的综合请求.
     * @param cache 要清除的cache
     * @param callback 当cache被清除或者没有对应cache的时候回调主线程
     */
    public ClearCacheRequest(Cache cache, Runnable callback) {
        super(Method.GET, null, null);
        mCache = cache;
        mCallback = callback;
    }
    // 实际执行的工作在这里
    @Override
    public boolean isCanceled() {
        // 先清除掉cache
        mCache.clear();
        if (mCallback != null) {
            //回调主线程，handler的Looper是主线程的Looper，则Handler在主线程处理消息
            Handler handler = new Handler(Looper.getMainLooper());
            //postAtFrontOfQueue把发送的任务放在消息队列的前边，保证尽早执行
            handler.postAtFrontOfQueue(mCallback);
        }
        return true;
    }
    // 获得当前请求的优先级
    @Override
    public Priority getPriority() {
        return Priority.IMMEDIATE;
    }
    // 该请求是用于清除cache的，没有响应，所以也不需要解析响应
    @Override
    protected Response<Object> parseNetworkResponse(NetworkResponse response) {
        return null;
    }
    // 同上
    @Override
    protected void deliverResponse(Object response) {
    }
}
