package com.android.volley;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 请求分发队列和分发器的线程池
 * 调用 {@link #add(Request)} 将会把给定的Request加入到分发队列中,通过本地缓存或者网络完成这个Request,
 * 随后把解析完毕后的响应信息传递到主线程中.
 */
public class RequestQueue {

    /** Request完成之后的回调接口. */
    public interface RequestFinishedListener<T> {
        /** Request的处理完成的时候调用这个接口. */
        void onRequestFinished(Request<T> request);
    }

    /** 用于生成请求的单调递增序列号. */
    private AtomicInteger mSequenceGenerator = new AtomicInteger();

    /**
     * 当一个request已经存在一个正在执行的另一个重复request的时候,会被放置到这个中转区域
     * <ul>
     *     <li>containsKey(cacheKey) 方法表示给定的Cache key已经存在一个正在执行的请求.</li>
     *     <li>get(cacheKey) 返回给定的cache key对应的正在等待执行的请求. 正在执行的请求不包含在返回列表中.如果没有请求被中转则返回null.</li>
     * </ul>
     */
    private final Map<String, Queue<Request<?>>> mWaitingRequests = new HashMap<String, Queue<Request<?>>>();

    /**
     * 当前正在被RequestQueue处理的request集合. 任何处于等待队列中的请求或者正在被调度器处理的请求都会被放置于该集合中.
     */
    private final Set<Request<?>> mCurrentRequests = new HashSet<Request<?>>();

    /** 缓存分类队列. */
    private final PriorityBlockingQueue<Request<?>> mCacheQueue = new PriorityBlockingQueue<Request<?>>();

    /** 网络分类队列. */
    private final PriorityBlockingQueue<Request<?>> mNetworkQueue = new PriorityBlockingQueue<Request<?>>();

    /** 网络请求分发器开启的数量，最大同时允许4个线程进行网络请求. */
    private static final int DEFAULT_NETWORK_THREAD_POOL_SIZE = 4;

    /** 用于检索和存储响应的高速缓存接口. */
    private final Cache mCache;

    /** 用于执行请求的Network接口. */
    private final Network mNetwork;

    /** 响应传递机制. */
    private final ResponseDelivery mDelivery;

    /** 网络调度器. */
    private NetworkDispatcher[] mDispatchers;

    /** 缓存调度器. */
    private CacheDispatcher mCacheDispatcher;
    /** 请求完成回调接口的集合 */
    private final List<RequestFinishedListener> mFinishedListeners = new ArrayList<RequestFinishedListener>();

    /**
     * 创建工作池. 当{@link #start()}方法被调用的时候就开始处理请求.
     *
     * @param cache 用于处理响应的缓存类
     * @param network 用于执行HTTP请求的网络接口
     * @param threadPoolSize 创建网络调度线程的数量
     * @param delivery 用于发布响应和错误的响应传递接口
     */
    public RequestQueue(Cache cache, Network network, int threadPoolSize, ResponseDelivery delivery) {
        mCache = cache;
        mNetwork = network;
        mDispatchers = new NetworkDispatcher[threadPoolSize];
        mDelivery = delivery;
    }

    /**
     * 创建工作池. 当{@link #start()}方法被调用的时候就开始处理请求.
     *
     * @param cache 用于处理响应的缓存类
     * @param network 用于执行HTTP请求的网络接口
     * @param threadPoolSize 创建网络调度线程的数量
     */
    public RequestQueue(Cache cache, Network network, int threadPoolSize) {
        this(cache, network, threadPoolSize, new ExecutorDelivery(new Handler(Looper.getMainLooper())));
    }

    /**
     * 创建工作池. 当{@link #start()}方法被调用的时候就开始处理请求.
     *
     * @param cache 用于处理响应的缓存类
     * @param network 用于执行HTTP请求的网络接口
     */
    public RequestQueue(Cache cache, Network network) {
        this(cache, network, DEFAULT_NETWORK_THREAD_POOL_SIZE);
    }

    /**
     * 开启队列调度.
     */
    public void start() {
        stop();  // 调用stop以保证当前所有正在运行的调度器都停止掉.
        // 创建并开启缓存调度.
        mCacheDispatcher = new CacheDispatcher(mCacheQueue, mNetworkQueue, mCache, mDelivery);
        mCacheDispatcher.start();

        // 根据相应的线程池大小创建网络调度器.
        for (int i = 0; i < mDispatchers.length; i++) {
            NetworkDispatcher networkDispatcher = new NetworkDispatcher(mNetworkQueue, mNetwork, mCache, mDelivery);
            mDispatchers[i] = networkDispatcher;
            // 开启网络调度器
            networkDispatcher.start();
        }
    }

    /**
     * 关闭缓存和网络调度器.
     */
    public void stop() {
        if (mCacheDispatcher != null) {
            mCacheDispatcher.quit();
        }
        for (NetworkDispatcher mDispatcher : mDispatchers) {
            if (mDispatcher != null) {
                mDispatcher.quit();
            }
        }
    }

    /**
     * 获取序列号.
     */
    public int getSequenceNumber() {
        return mSequenceGenerator.incrementAndGet();
    }

    /**
     * 获取正在使用的 {@link Cache} 实例.
     */
    public Cache getCache() {
        return mCache;
    }

    /**
     * 一个简单的过滤接口, 用于 {@link RequestQueue#cancelAll(RequestFilter)}.
     */
    public interface RequestFilter {
        boolean apply(Request<?> request);
    }

    /**
     * 取消此队列中的所有请求，该队列中的所有请求都适用 .
     * @param filter 要使用的过滤方法
     */
    public void cancelAll(RequestFilter filter) {
        synchronized (mCurrentRequests) {
            for (Request<?> request : mCurrentRequests) {
                if (filter.apply(request)) {
                    request.cancel();
                }
            }
        }
    }

    /**
     * 取消此队列中带有给定的tag的所有请求, Tag不能为null并且具有平等的标识.
     */
    public void cancelAll(final Object tag) {
        if (tag == null) {
            throw new IllegalArgumentException("Cannot cancelAll with a null tag");
        }
        // RequestFilter的作用在这里显示出来了
        cancelAll(new RequestFilter() {
            @Override
            public boolean apply(Request<?> request) {
                return request.getTag() == tag;
            }
        });
    }

    /**
     * 向调度队列中增加一个Request.
     * @param request 要服务的request
     * @return 被过滤器认为可以接受的request
     */
    public <T> Request<T> add(Request<T> request) {
        // 将请求标记为属于这个队列，并将其添加到当前请求的集合中.
        request.setRequestQueue(this);
        synchronized (mCurrentRequests) {
            mCurrentRequests.add(request);
        }

        // 按照他们被添加的顺序处理这些request.
        request.setSequence(getSequenceNumber());
        request.addMarker("add-to-queue");

        // 如果请求是不可缓存的,跳过缓存队列,直接走网络通信
        if (!request.shouldCache()) {
            mNetworkQueue.add(request);
            return request;
        }

        // Insert request into stage if there's already a request with the same cache key in flight.
        // 如果有一个相同的cache Key的request正在被执行,那么当前这个request就会被安排在中转区域
        synchronized (mWaitingRequests) {
            // 拿出当前request的key
            String cacheKey = request.getCacheKey();
            // 查看中转区域中是否存在当前request的key
            if (mWaitingRequests.containsKey(cacheKey)) {
                // 有一个相同的cache Key的request正在被执行. 把当前的request放进队列中.
                Queue<Request<?>> stagedRequests = mWaitingRequests.get(cacheKey);
                if (stagedRequests == null) {
                    stagedRequests = new LinkedList<Request<?>>();
                }
                stagedRequests.add(request);
                // 把队列放进中转区域
                mWaitingRequests.put(cacheKey, stagedRequests);
                if (VolleyLog.DEBUG) {
                    VolleyLog.v("Request for cacheKey=%s is in flight, putting on hold.", cacheKey);
                }
            } else {
                // 中转区域中没有该Key对应的请求队列，那么我们这个request就是第一个了。我们直接把key放进中转队列中做键
                // 但是要记得我们这第一个request是要被执行的
                // 为这个cacheKey插入一个null队列到中转区域, 标记有一个正在执行的请求.
                mWaitingRequests.put(cacheKey, null);
                // 放进缓存队列中
                mCacheQueue.add(request);
            }
            return request;
        }
    }

    /**
     * 该方法被 {@link Request#finish(String)}调用, 标记指定的request已经被处理完毕.
     * <p>Releases waiting requests for <code>request.getCacheKey()</code> if <code>request.shouldCache()</code>.</p>
     */
    <T> void finish(Request<T> request) {
        // 从当前正在处理的请求的集合中删除 .
        synchronized (mCurrentRequests) {
            mCurrentRequests.remove(request);
        }
        synchronized (mFinishedListeners) {
            // 挨个调用每一个FinishListener,告诉他们这个Request已经被执行完毕
          for (RequestFinishedListener<T> listener : mFinishedListeners) {
            listener.onRequestFinished(request);
          }
        }
        // request需要被缓存的时候,它有可能有相同CacheKey的request被驻留在中转区域
        if (request.shouldCache()) {
            synchronized (mWaitingRequests) {
                // 获取CacheKey
                String cacheKey = request.getCacheKey();
                // 从中转区域中拿到Key为CacheKey的等待队列
                Queue<Request<?>> waitingRequests = mWaitingRequests.remove(cacheKey);
                if (waitingRequests != null) {
                    if (VolleyLog.DEBUG) {
                        VolleyLog.v("Releasing %d waiting requests for cacheKey=%s.", waitingRequests.size(), cacheKey);
                    }
                    // 处理等待队列中所有的requests. 这些request不会再被一一的执行, 他们可以使用我们已经执行完毕的request给他们准备好的Cache.
                    mCacheQueue.addAll(waitingRequests);
                }
            }
        }
    }
    // 增加RequestFinishedListener回调
    public  <T> void addRequestFinishedListener(RequestFinishedListener<T> listener) {
      synchronized (mFinishedListeners) {
        mFinishedListeners.add(listener);
      }
    }

    /**
     * 移除一个RequestFinishedListener. 如果之前没有添加这个监听器,调用这个方法也不会有额外的影响.
     */
    public  <T> void removeRequestFinishedListener(RequestFinishedListener<T> listener) {
      synchronized (mFinishedListeners) {
        mFinishedListeners.remove(listener);
      }
    }
}
