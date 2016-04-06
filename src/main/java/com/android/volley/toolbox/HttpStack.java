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

import com.android.volley.AuthFailureError;
import com.android.volley.Request;

import org.apache.http.HttpResponse;

import java.io.IOException;
import java.util.Map;

/**
 * 一个 HTTP stack 接口.
 */
public interface HttpStack {
    /**
     * 使用给定的实现一个 HTTP 请求.
     *
     * <p>
     * 如果request.getPostBody() == null的话会发送GET请求.
     * 如果Content-Type header 被设置为request.getPostBodyContentType()和其他情况下,会发送POST请求.
     * </p>
     *
     * @param request 要执行的请求
     * @param additionalHeaders 要一起发送的头信息 {@link Request#getHeaders()}
     * @return HTTP 响应
     */
    public HttpResponse performRequest(Request<?> request, Map<String, String> additionalHeaders)
        throws IOException, AuthFailureError;

}
