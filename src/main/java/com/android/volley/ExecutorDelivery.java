package com.android.volley;

import android.os.Handler;

import java.util.concurrent.Executor;

/**
 * 传送响应信息和异常.
 */
public class ExecutorDelivery implements ResponseDelivery {
    /** 用于传递响应到主线程. */
    private final Executor mResponsePoster;

    /**
     * 创建一个新的响应传递接口.
     * @param handler {@link Handler} 要传递到的线程handler
     */
    public ExecutorDelivery(final Handler handler) {
        // 创建一个 Executor 来包装handler.
        mResponsePoster = new Executor() {
            @Override
            public void execute(Runnable command) {
                handler.post(command);
            }
        };
    }

    /**
     * 创建一个新的响应传递接口. 用于虚拟对象进行测试.
     * @param executor 运行传递任务
     */
    public ExecutorDelivery(Executor executor) {
        mResponsePoster = executor;
    }

    @Override
    public void postResponse(Request<?> request, Response<?> response) {
        postResponse(request, response, null);
    }

    @Override
    public void postResponse(Request<?> request, Response<?> response, Runnable runnable) {
        request.markDelivered();
        request.addMarker("post-response");
        mResponsePoster.execute(new ResponseDeliveryRunnable(request, response, runnable));
    }

    @Override
    public void postError(Request<?> request, VolleyError error) {
        request.addMarker("post-error");
        Response<?> response = Response.error(error);
        mResponsePoster.execute(new ResponseDeliveryRunnable(request, response, null));
    }

    /**
     * 用于传递响应信息到主线程监听器的Runnable.
     */
    @SuppressWarnings("rawtypes")
    private class ResponseDeliveryRunnable implements Runnable {
        private final Request mRequest;
        private final Response mResponse;
        private final Runnable mRunnable;

        public ResponseDeliveryRunnable(Request request, Response response, Runnable runnable) {
            mRequest = request;
            mResponse = response;
            mRunnable = runnable;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void run() {
            // 运行在主线程中
            // request已经取消，就不用再传递了
            if (mRequest.isCanceled()) {
                mRequest.finish("canceled-at-delivery");
                return;
            }
            // 传送响应或者异常
            if (mResponse.isSuccess()) {
                mRequest.deliverResponse(mResponse.result);
            } else {
                mRequest.deliverError(mResponse.error);
            }

            // 临时响应,增加一个标记, 否则的话我们的任务就完成了，request可以finish了.
            if (mResponse.intermediate) {
                mRequest.addMarker("intermediate-response");
            } else {
                mRequest.finish("done");
            }

            // 如果传递过来的还有runnable,需要把它执行完毕.
            if (mRunnable != null) {
                mRunnable.run();
            }
       }
    }
}
