package com.android.volley;

/**
 * 请求的重试策略.
 */
public interface RetryPolicy {

    /**
     * 返回超时时间.
     */
    int getCurrentTimeout();

    /**
     * 返回重试次数.
     */
    int getCurrentRetryCount();

    /**
     * 准备进行下一次重试.
     * @param error 上次请求的返回码.
     * @throws VolleyError 重试不能被执行的时候抛出.
     */
    void retry(VolleyError error) throws VolleyError;
}
