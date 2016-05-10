package com.android.volley;

import android.os.Process;

import java.util.concurrent.BlockingQueue;

/**
 * 为执行高速缓存分类的请求提供一个线程.
 *
 * 添加到指定的缓存队列的请求从缓存解析.
 * 任何可以返回的响应信息都会通过 {@link ResponseDelivery} 返回给调用者.
 * 丢失的缓存或者是要求刷新的响应会被插入到指定的网络队列，它将会被 {@link NetworkDispatcher} 处理.
 */
public class CacheDispatcher extends Thread {

    private static final boolean DEBUG = VolleyLog.DEBUG;

    /** 要执行的缓存队列. */
    private final BlockingQueue<Request<?>> mCacheQueue;

    /** 缓存队列. */
    private final BlockingQueue<Request<?>> mNetworkQueue;

    /** 要读取的缓存对象. */
    private final Cache mCache;

    /** 传递响应的Delivery. */
    private final ResponseDelivery mDelivery;

    /** 是否取消任务. */
    private volatile boolean mQuit = false;

    /**
     * 创建一个新的缓存分流调度线程 .  只有调用了 {@link #start()} 方法才会按照顺序处理请求.
     *
     * @param cacheQueue 缓存队列
     * @param networkQueue 网络队列
     * @param cache 要使用的Cache
     * @param delivery 响应回传工具
     */
    public CacheDispatcher(BlockingQueue<Request<?>> cacheQueue, BlockingQueue<Request<?>> networkQueue, Cache cache, ResponseDelivery delivery) {
        mCacheQueue = cacheQueue;
        mNetworkQueue = networkQueue;
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

    @Override
    public void run() {
        if (DEBUG) VolleyLog.v("开启一个新的调度器");
        // 设置线程优先级，THREAD_PRIORITY_BACKGROUND:后台线程
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

        // 初始化缓存对象.
        // 在子线程内部调用
        mCache.initialize();

        Request<?> request;
        // 无限循环
        while (true) {
            // 释放先前的请求对象来避免请求对象的泄漏
            // 这一句是有必要的
            request = null;
            try {
                // 从缓存队列中取出一个request.
                request = mCacheQueue.take();
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
                // 标记该request已经被取出
                request.addMarker("cache-queue-take");

                // 如果请求被取消，请不要打扰它 .
                if (request.isCanceled()) {
                    request.finish("cache-discard-canceled");
                    continue;
                }

                // 试图从缓存中检索这个项目 .
                Cache.Entry entry = mCache.get(request.getCacheKey());
                if (entry == null) {
                    request.addMarker("cache-miss");
                    // 缓存中找不到；发送到网络调度程序 .
                    mNetworkQueue.put(request);
                    continue;
                }

                // 缓存已经过期，发送到网络调度程序.
                if (entry.isExpired()) {
                    request.addMarker("cache-hit-expired");
                    request.setCacheEntry(entry);
                    mNetworkQueue.put(request);
                    continue;
                }

                // 找到对应的缓存; 解析数据返回给 request.
                request.addMarker("cache-hit");
                Response<?> response = request.parseNetworkResponse(new NetworkResponse(entry.data, entry.responseHeaders));
                request.addMarker("cache-hit-parsed");
                // 缓存数据需不需要刷新
                if (!entry.refreshNeeded()) {
                    // 缓存确实没有过期. 直接传递回去.
                    mDelivery.postResponse(request, response);
                } else {
                    // 软超时缓存. 我们可以把数据返回,同时也需要访问网络来刷新该缓存资源.
                    request.addMarker("cache-hit-refresh-needed");
                    request.setCacheEntry(entry);

                    // 打上这个,标识该 response 用的是软超时的缓存，需要刷新.
                    response.intermediate = true;

                    // 把数据立即传递给用户 然后我们需要立即请求网络获取新的资源.
                    final Request<?> finalRequest = request;
                    mDelivery.postResponse(request, response, new Runnable() {
                        @Override
                        public void run() {
                            try {
                                // 把请求放进网络队列中
                                mNetworkQueue.put(finalRequest);
                            } catch (InterruptedException e) {
                                // 除此之外我们做不了别的了.
                            }
                        }
                    });
                }
            } catch (Exception e) {
                VolleyLog.e(e, "未知异常 %s", e.toString());
            }
        }
    }
}
