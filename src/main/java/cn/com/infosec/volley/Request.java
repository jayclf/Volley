package cn.com.infosec.volley;

import android.net.TrafficStats;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import cn.com.infosec.volley.VolleyLog.MarkerLog;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.Map;

/**
 * 所有Request的基类.
 *
 * @param <T> 该Request期望解析的响应类型.
 */
public abstract class Request<T> implements Comparable<Request<T>> {

    /**
     * 默认的 POST 和 PUT 参数的编码. 看这里 {@link #getParamsEncoding()}.
     */
    private static final String DEFAULT_PARAMS_ENCODING = "UTF-8";

    /**
     * 该Request所支持的请求方式.
     */
    public interface Method {
        int DEPRECATED_GET_OR_POST = -1;
        int GET = 0;
        int POST = 1;
        int PUT = 2;
        int DELETE = 3;
        int HEAD = 4;
        int OPTIONS = 5;
        int TRACE = 6;
        int PATCH = 7;
    }

    /** 追踪Request生命周期的事件Log; 用于测试. */
    private final MarkerLog mEventLog = MarkerLog.ENABLED ? new MarkerLog() : null;

    /**
     * Request 的请求方式.  当前支持 GET, POST, PUT, DELETE, HEAD, OPTIONS,
     * TRACE, 和 PATCH.
     */
    private final int mMethod;

    /** 该Request的URL. */
    private final String mUrl;

    /** 重定向 url 用于3XX系列的响应 */
    private String mRedirectUrl;

    /** Request的唯一标识 */
    private String mIdentifier;

    /** 用于流量统计的Tag {@link TrafficStats}. */
    private final int mDefaultTrafficStatsTag;

    /** 异常以及错误监听器. */
    private Response.ErrorListener mErrorListener;

    /** Request的序列号, 用于进行 FIFO 排序. */
    private Integer mSequence;

    /** 该Request所在的请求队列. */
    private RequestQueue mRequestQueue;

    /** 是否应缓存该请求的响应数据. */
    private boolean mShouldCache = true;

    /** 是否该请求已被取消. */
    private boolean mCanceled = false;

    /** 该Request的响应是否已经发送 . */
    private boolean mResponseDelivered = false;

    /** Request的重试策略. */
    private RetryPolicy mRetryPolicy;

    /**
     * 当一个请求可以从cache中检索到但是又必须从网络中刷新时, 缓存的entry将会被存储到这里,以防接收到 "Not Modified" 响应的时候, 来保证它还没有从cache中删除掉.
     */
    private Cache.Entry mCacheEntry = null;

    /** 使用一个不公开的令牌标记这个请求; 用于批量取消. */
    private Object mTag;

    /**
     * 使用给定的 URL 和 error listener创建一个新的Request.
     * 请注意,在这里不提供正常的响应侦听器来提供响应的传递,它的子类有更好解析响应的方式。
     * @deprecated 使用 {@link #Request(int, String, Response.ErrorListener)} 将是更好的选择.
     */
    @Deprecated
    public Request(String url, Response.ErrorListener listener) {
        this(Method.DEPRECATED_GET_OR_POST, url, listener);
    }

    /**
     * 使用给定的 URL,error listener以及{@link Method}创建一个新的Request.  Note that the normal response listener is not provided here as
     * 请注意,在这里不提供正常的响应侦听器来提供响应的传递,它的子类有更好解析响应的方式.
     */
    public Request(int method, String url, Response.ErrorListener listener) {
        mMethod = method;
        mUrl = url;
        mIdentifier = createIdentifier(method, url);
        mErrorListener = listener;
        setRetryPolicy(new DefaultRetryPolicy());

        mDefaultTrafficStatsTag = findDefaultTrafficStatsTag(url);
    }

    /**
     * 返回Request的请求方式. 是 {@link Method} 其中之一.
     */
    public int getMethod() {
        return mMethod;
    }

    /**
     * 为Request设置一个Tag. 可以通过调用{@link RequestQueue#cancelAll(Object)}方法利用该Tag取消所有的Request .
     *
     * @return 这个允许链接的请求对象.
     */
    public Request<?> setTag(Object tag) {
        mTag = tag;
        return this;
    }

    /**
     * 返回Request的 tag.
     * @see Request#setTag(Object)
     */
    public Object getTag() {
        return mTag;
    }

    /**
     * @return 该Request的 {@link Response.ErrorListener}.
     */
    public Response.ErrorListener getErrorListener() {
        return mErrorListener;
    }

    /**
     * @return 用于 {@link TrafficStats#setThreadStatsTag(int)} 的tag
     */
    public int getTrafficStatsTag() {
        return mDefaultTrafficStatsTag;
    }

    /**
     * @return URL主机组件的hashCode，如果没有返回0.
     */
    private static int findDefaultTrafficStatsTag(String url) {
        if (!TextUtils.isEmpty(url)) {
            Uri uri = Uri.parse(url);
            if (uri != null) {
                String host = uri.getHost();
                if (host != null) {
                    return host.hashCode();
                }
            }
        }
        return 0;
    }

    /**
     * 设置Request的重试策略.
     *
     * @return 这个允许链接的请求对象.
     */
    public Request<?> setRetryPolicy(RetryPolicy retryPolicy) {
        mRetryPolicy = retryPolicy;
        return this;
    }

    /**
     * 增加一个事件到请求的事件Log; 用于测试.
     */
    public void addMarker(String tag) {
        if (MarkerLog.ENABLED) {
            mEventLog.add(tag, Thread.currentThread().getId());
        }
    }

    /**
     * 通知请求队列，这个请求已经完成（成功或错误）.
     *
     * <p>同时Dump该Request的所有事件Log;</p>
     */
    public void finish(final String tag) {
        if (mRequestQueue != null) {
            mRequestQueue.finish(this);
            onFinish();
        }
        // Dumping Log
        if (MarkerLog.ENABLED) {
            final long threadId = Thread.currentThread().getId();
            if (Looper.myLooper() != Looper.getMainLooper()) {
                // 如果没有在主线程完成标记, 我们需要在主线程Dump以保证正确的序列.
                Handler mainThread = new Handler(Looper.getMainLooper());
                mainThread.post(new Runnable() {
                    @Override
                    public void run() {
                        mEventLog.add(tag, threadId);
                        mEventLog.finish(this.toString());
                    }
                });
                return;
            }
            mEventLog.add(tag, threadId);
            mEventLog.finish(this.toString());
        }
    }

    /**
     * 当finish的时候清除所有的监听器
     */
    protected void onFinish() {
        mErrorListener = null;
    }

    /**
     * 将此请求与给定队列关联.
     * 请求队列将在该请求完成时通知 .
     *
     * @return 这个允许链接的请求对象.
     */
    public Request<?> setRequestQueue(RequestQueue requestQueue) {
        mRequestQueue = requestQueue;
        return this;
    }

    /**
     * 设置此请求的序列号.  用于 {@link RequestQueue}.
     *
     * @return 这个允许链接的请求对象.
     */
    public final Request<?> setSequence(int sequence) {
        mSequence = sequence;
        return this;
    }

    /**
     * @return 请求的序列号.
     */
    public final int getSequence() {
        if (mSequence == null) {
            throw new IllegalStateException("getSequence called before setSequence");
        }
        return mSequence;
    }

    /**
     * @return 返回请求的URL.
     */
    public String getUrl() {
        return (mRedirectUrl != null) ? mRedirectUrl : mUrl;
    }

    /**
     * @return 返回重定向发生之前请求的URL.
     */
    public String getOriginUrl() {
    	return mUrl;
    }

    /**
     * @return 返回Request的标记符号.
     */
    public String getIdentifier() {
        return mIdentifier;
    }

    /**
     * 设置重定向URL来处理 3xx 类别的http响应.
     */
    public void setRedirectUrl(String redirectUrl) {
    	mRedirectUrl = redirectUrl;
    }

    /**
     * 返回Request的Cache key.  默认情况下，就是该Request的 URL.
     */
    public String getCacheKey() {
        return mMethod + ":" + mUrl;
    }

    /**
     * 注释这个请求从缓存条目检索.
     * 用于高速缓存一致性的支持.
     *
     * @return 这个允许链接的请求对象.
     */
    public Request<?> setCacheEntry(Cache.Entry entry) {
        mCacheEntry = entry;
        return this;
    }

    /**
     * 返回已经注释的 cache entry, 没有的话返回null.
     */
    public Cache.Entry getCacheEntry() {
        return mCacheEntry;
    }

    /**
     * 标记该请求已经Cacle.没有回调会被传送.
     */
    public void cancel() {
        mCanceled = true;
    }

    /**
     * 如果Request被取消返回true.
     */
    public boolean isCanceled() {
        return mCanceled;
    }

    /**
     * 返回一个额外的HTTP标头列表 .
     * 会抛出 {@link AuthFailureError} 异常，如果要求授权.
     * @throws AuthFailureError 授权失败抛出
     */
    public Map<String, String> getHeaders() throws AuthFailureError {
        return Collections.emptyMap();
    }

    /**
     * 返回一个该Request使用的 POST 参数的Map, 如果使用了一个简单的GET则返回null.
     * 会抛出 {@link AuthFailureError} 异常.
     *
     * <p>注意只有一个 getPostParams() 和 getPostBody() 会返回一个非空结果.</p>
     * @throws AuthFailureError 授权失败抛出
     *
     * @deprecated 使用 {@link #getParams()} 替代.
     */
    @Deprecated
    protected Map<String, String> getPostParams() throws AuthFailureError {
        return getParams();
    }

    /**
     * 返回 转换 POST 参数为原始的POST内容所使用的编码.
     *
     * <p>控制两个编码:
     * <ol>
     *     <li>String编码 当转换参数名和值为byte数组 bytes,进行URL编码的时候.</li>
     *     <li>String编码 当转换URL为原始byte数组.</li>
     * </ol>
     *
     * @deprecated 使用 {@link #getParamsEncoding()} 替代.
     */
    @Deprecated
    protected String getPostParamsEncoding() {
        return getParamsEncoding();
    }

    /**
     * @deprecated 使用 {@link #getBodyContentType()} 替代.
     */
    @Deprecated
    public String getPostBodyContentType() {
        return getBodyContentType();
    }

    /**
     * 返回要发送的原始的 POST body.
     *
     * @throws AuthFailureError 授权失败抛出
     *
     * @deprecated 使用 {@link #getBody()} 替代.
     */
    @Deprecated
    public byte[] getPostBody() throws AuthFailureError {
        // 注意: 为了兼容旧的Volley客户端,这种实现必须留在这里而不是简单地调用getbody()功能,因为这个函数必须调用getpostparams()和getpostparamsencoding()以来遗留客户会重写这两个成员函数POST请求 .
        Map<String, String> postParams = getPostParams();
        if (postParams != null && postParams.size() > 0) {
            return encodeParameters(postParams, getPostParamsEncoding());
        }
        return null;
    }

    /**
     * 返回 用于 POST 或者 PUT 请求使用的参数map.
     * 抛出{@link AuthFailureError} 异常.
     *
     * <p>可以直接重写 {@link #getBody()} 用于自定义数据.</p>
     *
     * @throws AuthFailureError 授权失败抛出
     */
    protected Map<String, String> getParams() throws AuthFailureError {
        return null;
    }

    /**
     * 返回 转换 POST 或者 PUT 参数为原始的POST内容所使用的编码 {@link #getParams()}.
     *
     * <p>控制两个编码:
     * <ol>
     *     <li>String编码 当转换参数名和值为byte数组 bytes,进行URL编码的时候.</li>
     *     <li>String编码 当转换URL为原始byte数组.</li>
     * </ol>
     */
    protected String getParamsEncoding() {
        return DEFAULT_PARAMS_ENCODING;
    }

    /**
     * 返回 POST 或者 PUT 体的content type.
     */
    public String getBodyContentType() {
        return "application/x-www-form-urlencoded; charset=" + getParamsEncoding();
    }

    /**
     * 返回要发送的原始的 POST 或者 PUT的body.
     *
     * <p>默认, 该body 包含application/x-www-form-urlencoded格式的请求参数. 重写该方法的时候,推荐也重写
     * {@link #getBodyContentType()} 方法来和新的body格式保持一致.
     *
     * @throws AuthFailureError 授权失败抛出
     */
    public byte[] getBody() throws AuthFailureError {
        Map<String, String> params = getParams();
        if (params != null && params.size() > 0) {
            return encodeParameters(params, getParamsEncoding());
        }
        return null;
    }

    /**
     * 转换 <code>params</code> 为一个 application/x-www-form-urlencoded 编码的字符串.
     */
    private byte[] encodeParameters(Map<String, String> params, String paramsEncoding) {
        StringBuilder encodedParams = new StringBuilder();
        try {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                encodedParams.append(URLEncoder.encode(entry.getKey(), paramsEncoding));
                encodedParams.append('=');
                encodedParams.append(URLEncoder.encode(entry.getValue(), paramsEncoding));
                encodedParams.append('&');
            }
            return encodedParams.toString().getBytes(paramsEncoding);
        } catch (UnsupportedEncodingException uee) {
            throw new RuntimeException("不支持的编码格式: " + paramsEncoding, uee);
        }
    }

    /**
     * 设置该Request的相应信息是否应该被缓存起来.
     *
     * @return 这个允许链接的请求对象.
     */
    public final Request<?> setShouldCache(boolean shouldCache) {
        mShouldCache = shouldCache;
        return this;
    }

    /**
     * @return 该Request的响应信息可以缓存的时候返回true.
     */
    public final boolean shouldCache() {
        return mShouldCache;
    }

    /**
     * 优先级值.  请求将会按照FIFO的顺序按照优先级从高到底一个一个来处理.
     */
    public enum Priority {
        LOW,
        NORMAL,
        HIGH,
        IMMEDIATE
    }

    /**
     * @return 返回Request的优先级 {@link Priority}; 默认是{@link Priority#NORMAL}.
     */
    public Priority getPriority() {
        return Priority.NORMAL;
    }

    /**
     * 返回每次重试的Socket超时毫秒数. (改值可以通过 backoffTimeout()方法设置backoff来修改).
     * 如果没有重试机会, 将会抛出 {@link TimeoutError} 错误.
     */
    public final int getTimeoutMs() {
        return mRetryPolicy.getCurrentTimeout();
    }

    /**
     * 返回该Request的重试策略.
     */
    public RetryPolicy getRetryPolicy() {
        return mRetryPolicy;
    }

    /**
     * 标记改Request已经将响应回传了. 这可以用于在请求的生命周期内避免相同的Response.
     */
    public void markDelivered() {
        mResponseDelivered = true;
    }

    /**
     * 如果这个请求已经为它提供了一个响应，则返回真.
     */
    public boolean hasHadResponseDelivered() {
        return mResponseDelivered;
    }

    /**
     * 子类必须实现该方法用于解析原始的网络响应信息并且返回合适的响应类型.
     * 该方法被子线程调用.返回null响应将不会被送达.
     * @param response 网络响应
     * @return 已经解析的响应信息，发生错误返回null
     */
    abstract protected Response<T> parseNetworkResponse(NetworkResponse response);

    /**
     * 子类可以重写该方法 来解析 'networkError' 来返回一个更加确切的错误信息.
     *
     * <p>默认的实现只返回一个解析后的 'networkError'.</p>
     *
     * @param volleyError 从网络接收到的error
     * @return 一个包含额外信息的NetworkError
     */
    protected VolleyError parseNetworkError(VolleyError volleyError) {
        return volleyError;
    }

    /**
     * 子类必须实现该方法来传输响应信息到 listeners.
     * 给定的response是不会为null的;
     * 失败的相应信息不会被传递到这里.
     * @param response 已经解析后的响应信息 {@link #parseNetworkResponse(NetworkResponse)}
     */
    abstract protected void deliverResponse(T response);

    /**
     * 传送错误信息到 ErrorListener.
     *
     * @param error 错误信息对象
     */
    public void deliverError(VolleyError error) {
        if (mErrorListener != null) {
            mErrorListener.onErrorResponse(error);
        }
    }

    /**
     * 使用比较器按照优先级从高到低排列, 其次按照序列号以FIFO顺序排列.
     */
    @Override
    public int compareTo(Request<T> other) {
        Priority left = this.getPriority();
        Priority right = other.getPriority();

        // 高优先级比较 "轻量" 所以他们排到前边.
        // 使用序列号的FIFO队列来比较优先级.
        return left == right ? this.mSequence - other.mSequence : right.ordinal() - left.ordinal();
    }

    @Override
    public String toString() {
        String trafficStatsTag = "0x" + Integer.toHexString(getTrafficStatsTag());
        return (mCanceled ? "[X] " : "[ ] ") + getUrl() + " " + trafficStatsTag + " "
                + getPriority() + " " + mSequence;
    }

    private static long sCounter;
    /**
     * 生成Request的ID标识
     * 对:(Request:method:url:timestamp:counter)做SHA1得到
     * @param method HTTP方法
     * @param url 请求的URL
     * @return sha1 hash字符串
     */
    private static String createIdentifier(final int method, final String url) {
        return InternalUtils.sha1Hash("Request:" + method + ":" + url + ":" + System.currentTimeMillis() + ":" + (sCounter++));
    }
}
