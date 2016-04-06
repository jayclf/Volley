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

import android.os.SystemClock;

import com.android.volley.AuthFailureError;
import com.android.volley.Cache;
import com.android.volley.Cache.Entry;
import com.android.volley.Network;
import com.android.volley.NetworkError;
import com.android.volley.NetworkResponse;
import com.android.volley.NoConnectionError;
import com.android.volley.RedirectError;
import com.android.volley.Request;
import com.android.volley.RetryPolicy;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.cookie.DateUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 通过 {@link HttpStack}执行一个网络请求.
 */
public class BasicNetwork implements Network {
    protected static final boolean DEBUG = VolleyLog.DEBUG;
    //指定慢请求的时间阈值
    private static int SLOW_REQUEST_THRESHOLD_MS = 3000;
    //默认Buffer池大小
    private static int DEFAULT_POOL_SIZE = 4096;
    //传递进来的HttpStack
    protected final HttpStack mHttpStack;
    //ByteArrayPool是一个Byte数组List
    protected final ByteArrayPool mPool;

    /**
     * @param httpStack 要使用的HTTP stack
     */
    public BasicNetwork(HttpStack httpStack) {
        // 如果不传入ByteArrayPool, 那就创建一个小的默认大小的ByteArrayPool
        // 这样很好，因为我们不需要太多的内存
        this(httpStack, new ByteArrayPool(DEFAULT_POOL_SIZE));
    }

    /**
     * @param httpStack 要使用的HTTP stack
     * @param pool 一个可以提高复制操作中GC性能的缓冲池
     */
    public BasicNetwork(HttpStack httpStack, ByteArrayPool pool) {
        mHttpStack = httpStack;
        mPool = pool;
    }

    @Override
    public NetworkResponse performRequest(Request<?> request) throws VolleyError {
        //记录请求开始的时间
        long requestStart = SystemClock.elapsedRealtime();
        //开始无限循环,是用于处理用户提交的请求的嘛?
        while (true) {
            //定义接收数据的容器
            HttpResponse httpResponse = null;
            byte[] responseContents = null;
            Map<String, String> responseHeaders = Collections.emptyMap();
            try {
                // 收集请求头.
                Map<String, String> headers = new HashMap<String, String>();
                //获取Request的Cache信息
                addCacheHeaders(headers, request.getCacheEntry());
                //调用HttpStack执行请求，返回的对象是apache的HttpRequest
                httpResponse = mHttpStack.performRequest(request, headers);
                //获取响应栏和响应码
                StatusLine statusLine = httpResponse.getStatusLine();
                int statusCode = statusLine.getStatusCode();
                //获取响应头
                responseHeaders = convertHeaders(httpResponse.getAllHeaders());
                // 验证Cache.
                if (statusCode == HttpStatus.SC_NOT_MODIFIED) {
                    // 响应码：304 （未修改） 自从上次请求后，请求的网页未修改过。 服务器返回此响应时，不会返回网页内容。
                    Entry entry = request.getCacheEntry();
                    if (entry == null) {
                        //返回304且没有缓存，直接结束请求并返回一个新的NetworkResponse
                        return new NetworkResponse(HttpStatus.SC_NOT_MODIFIED, null, responseHeaders, true, SystemClock.elapsedRealtime() - requestStart);
                    }
                    // 有缓存
                    // HTTP 304 响应并不返回所有的头字段. 我们还需要使用 cache entry的头字段加上返回的头字段.
                    // http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.3.5
                    entry.responseHeaders.putAll(responseHeaders);
                    //数据没有改变,我们只需要返回Cache中的数据和新的响应头
                    return new NetworkResponse(HttpStatus.SC_NOT_MODIFIED, entry.data, entry.responseHeaders, true, SystemClock.elapsedRealtime() - requestStart);
                }

                // 响应码：301   （永久移动）  请求的网页已永久移动到新位置。 服务器返回此响应（对 GET 或 HEAD 请求的响应）时，会自动将请求者转到新位置
                // 响应码：302   （临时移动）  服务器目前从不同位置的网页响应请求，但请求者应继续使用原有位置来进行以后的请求
                if (statusCode == HttpStatus.SC_MOVED_PERMANENTLY || statusCode == HttpStatus.SC_MOVED_TEMPORARILY) {
                    // 从响应头中获取资源新的URL
                	String newUrl = responseHeaders.get("Location");
                    // 把请求重定向到新的URL
                	request.setRedirectUrl(newUrl);
                }

                // 另外有些204之类的响应没有内容. 我们必须检查这类情况.
                if (httpResponse.getEntity() != null) {
                    //获取响应内容
                  responseContents = entityToBytes(httpResponse.getEntity());
                } else {
                  // 没有内容的话，我们就诚实的返回一个长度为0的byte数组.
                  responseContents = new byte[0];
                }
                // 如果请求很慢,则需要打印出来
                long requestLifetime = SystemClock.elapsedRealtime() - requestStart;
                logSlowRequests(requestLifetime, request, responseContents, statusLine);
                // 响应码：小于200或者大于299,其余的响应都表示该请求失败
                if (statusCode < 200 || statusCode > 299) {
                    throw new IOException();
                }
                // 返回响应内容，封装为NetworkResponse
                return new NetworkResponse(statusCode, responseContents, responseHeaders, false, SystemClock.elapsedRealtime() - requestStart);
            } catch (SocketTimeoutException e) {
                // 发生超时,准备重试
                attemptRetryOnException("socket", request, new TimeoutError());
            } catch (ConnectTimeoutException e) {
                // 发生超时,准备重试
                attemptRetryOnException("connection", request, new TimeoutError());
            } catch (MalformedURLException e) {
                // 无效的URL,直接抛出异常
                throw new RuntimeException("Bad URL " + request.getUrl(), e);
            } catch (IOException e) {
                // 抛出IOException的地方有很多，我们需要区别对待
                // 获取状态码
                int statusCode;
                NetworkResponse networkResponse;
                if (httpResponse != null) {
                    statusCode = httpResponse.getStatusLine().getStatusCode();
                } else {
                    throw new NoConnectionError(e);
                }
                // 301.302,打印被重定向的URL，其余响应码直接打印进Log
                if (statusCode == HttpStatus.SC_MOVED_PERMANENTLY || statusCode == HttpStatus.SC_MOVED_TEMPORARILY) {
                	VolleyLog.e("Request at %s has been redirected to %s", request.getOriginUrl(), request.getUrl());
                } else {
                	VolleyLog.e("Unexpected response code %d for %s", statusCode, request.getUrl());
                }
                // 判断响应内容
                if (responseContents != null) {
                    // 封装响应内容
                    networkResponse = new NetworkResponse(statusCode, responseContents, responseHeaders, false, SystemClock.elapsedRealtime() - requestStart);
                    // 响应码：401   （未授权） 请求要求身份验证。 对于需要登录的网页，服务器可能返回此响应。
                    // 响应码：403   （禁止） 服务器拒绝请求。
                    if (statusCode == HttpStatus.SC_UNAUTHORIZED || statusCode == HttpStatus.SC_FORBIDDEN) {
                        // 尝试重新请求
                        attemptRetryOnException("auth", request, new AuthFailureError(networkResponse));
                    } else if (statusCode == HttpStatus.SC_MOVED_PERMANENTLY || statusCode == HttpStatus.SC_MOVED_TEMPORARILY) {
                        // 301.302,重新请求新的URL
                        attemptRetryOnException("redirect", request, new RedirectError(networkResponse));
                    } else {
                        // TODO: 5xx 系列的返回码,客户端无能为力,只能抛异常.
                        throw new ServerError(networkResponse);
                    }
                } else {
                    // 响应内容为空
                    throw new NetworkError(e);
                }
            }
        }
    }

    /**
     * Logs requests that took over SLOW_REQUEST_THRESHOLD_MS to complete.
     */
    private void logSlowRequests(long requestLifetime, Request<?> request, byte[] responseContents, StatusLine statusLine) {
        if (DEBUG || requestLifetime > SLOW_REQUEST_THRESHOLD_MS) {
            VolleyLog.d("HTTP response for request=<%s> [lifetime=%d], [size=%s], " +
                    "[rc=%d], [retryCount=%s]", request, requestLifetime,
                    responseContents != null ? responseContents.length : "null",
                    statusLine.getStatusCode(), request.getRetryPolicy().getCurrentRetryCount());
        }
    }

    /**
     * 为再次请求准备一个Request. 如果没有重试次数的话，就抛出超时异常.
     * @param request The request to use.
     */
    private static void attemptRetryOnException(String logPrefix, Request<?> request, VolleyError exception) throws VolleyError {
        // 从Request中过去重试策略
        RetryPolicy retryPolicy = request.getRetryPolicy();
        // 获取我们上一次请求的用时
        int oldTimeout = request.getTimeoutMs();

        try {
            // 进行重试
            retryPolicy.retry(exception);
        } catch (VolleyError e) {
            // 连重试也失败的话，那就彻底没机会咯
            request.addMarker(String.format("%s-timeout-giveup [timeout=%s]", logPrefix, oldTimeout));
            throw e;
        }
        request.addMarker(String.format("%s-retry [timeout=%s]", logPrefix, oldTimeout));
    }

    private void addCacheHeaders(Map<String, String> headers, Cache.Entry entry) {
        // 没有Cache就算了.
        if (entry == null) {
            return;
        }
        /**
         * 有Cache的话就把Entry中的etag添加到Map中,map的Key是"If-None-Match"
         * ETag是一个可以与Web资源关联的记号（token）
         * 用于标识Request请求的资源
         */
        if (entry.etag != null) {
            headers.put("If-None-Match", entry.etag);
        }
        /**
         * 把Entry中的lastModified添加到Map中,Key为"If-Modified-Since"
         * lastModified是资源最后的修改时间，常用于判断是否缓存
         */
        if (entry.lastModified > 0) {
            Date refTime = new Date(entry.lastModified);
            headers.put("If-Modified-Since", DateUtils.formatDate(refTime));
        }
    }

    protected void logError(String what, String url, long start) {
        long now = SystemClock.elapsedRealtime();
        VolleyLog.v("HTTP ERROR(%s) %d ms to fetch %s", what, (now - start), url);
    }

    /** Reads the contents of HttpEntity into a byte[]. */
    private byte[] entityToBytes(HttpEntity entity) throws IOException, ServerError {
        PoolingByteArrayOutputStream bytes =
                new PoolingByteArrayOutputStream(mPool, (int) entity.getContentLength());
        byte[] buffer = null;
        try {
            InputStream in = entity.getContent();
            if (in == null) {
                throw new ServerError();
            }
            buffer = mPool.getBuf(1024);
            int count;
            while ((count = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, count);
            }
            return bytes.toByteArray();
        } finally {
            try {
                // Close the InputStream and release the resources by "consuming the content".
                entity.consumeContent();
            } catch (IOException e) {
                // This can happen if there was an exception above that left the entity in
                // an invalid state.
                VolleyLog.v("Error occured when calling consumingContent");
            }
            mPool.returnBuf(buffer);
            bytes.close();
        }
    }

    /**
     * Converts Headers[] to Map<String, String>.
     */
    protected static Map<String, String> convertHeaders(Header[] headers) {
        Map<String, String> result = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < headers.length; i++) {
            result.put(headers[i].getName(), headers[i].getValue());
        }
        return result;
    }
}
