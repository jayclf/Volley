package com.android.volley;

import android.annotation.TargetApi;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;

import java.util.concurrent.BlockingQueue;

/**
 * 为执行网络请求分类的请求提供一个线程.
 *
 * 添加到指定队列的request 被 {@link Network} 接口通过网络处理.
 * 响应信息被提交到了缓存中存储, 使用的是 {@link Cache} 接口.
 * 有效的响应信息和错误信息都会被 {@link ResponseDelivery} 回传.
 */
public class NetworkDispatcher extends Thread {
    /** 存储request的队列. */
    private final BlockingQueue<Request<?>> mQueue;
    /** 操作网络的接口. */
    private final Network mNetwork;
    /** 操作缓存的接口. */
    private final Cache mCache;
    /** 响应回传工具. */
    private final ResponseDelivery mDelivery;
    /** 退出标记. */
    private volatile boolean mQuit = false;

    /**
     * 创建一个新的网络分流调度线程.只有调用了 {@link #start()} 方法才会按照顺序处理请求.
     *
     * @param queue 处理队列
     * @param network 处理request的网络接口
     * @param cache 缓存接口
     * @param delivery 响应回传器
     */
    public NetworkDispatcher(BlockingQueue<Request<?>> queue, Network network, Cache cache, ResponseDelivery delivery) {
        mQueue = queue;
        mNetwork = network;
        mCache = cache;
        mDelivery = delivery;
    }

    /**
     * 强制性立即停止调度器.  如果在队列中还有其他请求, 它们不能保证被处理 .
     */
    public void quit() {
        mQuit = true;
        interrupt();
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void addTrafficStatsTag(Request<?> request) {
        // 目标API >= 14
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            /**
             * 流量状态查询
             * 使用TrafficStats.setThreadStatsTag()方法来标记线程内部发生的数据传输情况
             * Apache的HttpClient和URLConnection类库会基于当前的getThreadStatsTag()方法的返回值来自动的标记网络套接字。这些类库也可以通过活动的保持池（keep-alive pools）标记网络套接字，并在回收时解除标记。
             * 网络套接字标记在Android4.0以后被支持，但是实时的统计结果只会被显示在运行Android4.0.3以后的设备上。
             * @see http://www.2cto.com/kf/201312/262627.html
             */
            TrafficStats.setThreadStatsTag(request.getTrafficStatsTag());
        }
    }

    @Override
    public void run() {
        // 线程优先级设置
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        Request<?> request;
        while (true) {
            // 统计执行时间
            long startTimeMs = SystemClock.elapsedRealtime();
            // 释放先前的请求对象来避免请求对象的泄漏
            request = null;
            try {
                // 从网络队列中取出一个request.
                request = mQueue.take();
            } catch (InterruptedException e) {
                // 取出request发生中断
                // 如果外部要求退出循环，在这里return退出无限循环.
                if (mQuit) {
                    return;
                }
                // 否则继续向下进行下一个请求
                continue;
            }

            try {
                request.addMarker("network-queue-take");

                // 如果请求被取消，请不要打扰它 .
                if (request.isCanceled()) {
                    request.finish("network-discard-cancelled");
                    continue;
                }
                // 为该线程统计流量
                addTrafficStatsTag(request);

                // 执行网络请求.
                NetworkResponse networkResponse = mNetwork.performRequest(request);
                request.addMarker("network-http-complete");

                // 服务器返回 304 并且我们已经回传了一个响应,
                // 搞定 -- 不需要发送一个相同的响应.
                if (networkResponse.notModified && request.hasHadResponseDelivered()) {
                    request.finish("not-modified");
                    continue;
                }

                // 解析响应消息.
                Response<?> response = request.parseNetworkResponse(networkResponse);
                request.addMarker("network-parse-complete");

                // 写入到cache中.
                // TODO: 304 系列的响应信息只需要更新元数据而不需要更新内容.
                if (request.shouldCache() && response.cacheEntry != null) {
                    mCache.put(request.getCacheKey(), response.cacheEntry);
                    request.addMarker("network-cache-written");
                }

                // 回传响应信息.
                request.markDelivered();
                mDelivery.postResponse(request, response);
            } catch (VolleyError volleyError) {
                volleyError.setNetworkTimeMs(SystemClock.elapsedRealtime() - startTimeMs);
                parseAndDeliverNetworkError(request, volleyError);
            } catch (Exception e) {
                VolleyLog.e(e, "Unhandled exception %s", e.toString());
                VolleyError volleyError = new VolleyError(e);
                volleyError.setNetworkTimeMs(SystemClock.elapsedRealtime() - startTimeMs);
                mDelivery.postError(request, volleyError);
            }
        }
    }

    private void parseAndDeliverNetworkError(Request<?> request, VolleyError error) {
        error = request.parseNetworkError(error);
        mDelivery.postError(request, error);
    }
}
