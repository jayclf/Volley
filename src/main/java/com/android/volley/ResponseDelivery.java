package com.android.volley;

public interface ResponseDelivery {
    /**
     * 把从网络或者缓存中的数据回传给调用者.
     */
    void postResponse(Request<?> request, Response<?> response);

    /**
     * 把从网络或者缓存中的数据回传给调用者.
     * Runnable将会在数据传递之后立即执行.
     */
    void postResponse(Request<?> request, Response<?> response, Runnable runnable);

    /**
     * 为指定的request回传异常.
     */
    void postError(Request<?> request, VolleyError error);
}
