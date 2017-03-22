package cn.com.infosec.volley.toolbox;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;

import cn.com.infosec.volley.Network;
import cn.com.infosec.volley.RequestQueue;

import java.io.File;

/**
 * @author clf
 * Volley框架的起始点，通常我们都会这样做
 * Volley.newRequestQueue
 */
public class Volley {

    /** 磁盘上的默认缓存目录. */
    private static final String DEFAULT_CACHE_DIR = "volley";

    /**
     * 创建一个请求队列的实例并且在它上面调用 {@link RequestQueue#start()} 开启该队列.
     * 你可以以bytes单位来设置磁盘缓存的最大空间.
     *
     * @param context 用户创建缓存目录的 {@link Context}.
     * @param stack 用于网络连接的 {@link HttpStack} 默认为null.
     * @param maxDiskCacheBytes 磁盘缓存的最大值, 单位是bytes. 传-1表示使用默认值.
     * @return 已经开启的 {@link RequestQueue} 实例.
     */
    public static RequestQueue newRequestQueue(Context context, HttpStack stack, int maxDiskCacheBytes) {
        //创建缓存文件
        File cacheDir = new File(context.getCacheDir(), DEFAULT_CACHE_DIR);
        //该userAgent用于创建HttpClientStack，后面会看到
        String userAgent = "volley/0";
        try {
            //最终userAgent的形式是"包名/应用版本号"
            String packageName = context.getPackageName();
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            userAgent = packageName + "/" + info.versionCode;
        } catch (NameNotFoundException e) {
            //虽说一般情况下不会发生此类异常，但是对于处女座这样子还是不太好...
            //打印一下下吧
            e.printStackTrace();
        }

        Network network = new BasicNetwork(stack == null?new HurlStack():stack);
        
        RequestQueue queue;
        if (maxDiskCacheBytes <= -1)
        {
        	// No maximum size specified
        	queue = new RequestQueue(new DiskBasedCache(cacheDir), network);
        }
        else
        {
        	// Disk cache size specified
        	queue = new RequestQueue(new DiskBasedCache(cacheDir, maxDiskCacheBytes), network);
        }

        queue.start();

        return queue;
    }

    /**
     * 方法重载，用户无需指定HttpStack
     *
     * @param context 用户创建缓存目录的 {@link Context}.
     * @param maxDiskCacheBytes 磁盘缓存的最大值, 单位是bytes. 传-1表示使用默认值.
     * @return 已经开启的 {@link RequestQueue} 实例.
     */
    public static RequestQueue newRequestQueue(Context context, int maxDiskCacheBytes) {
        return newRequestQueue(context, null, maxDiskCacheBytes);
    }
    
    /**
     * 方法重载，使用默认缓存大小
     *
     * @param context 用户创建缓存目录的 {@link Context}.
     * @param stack 用于网络连接的 {@link HttpStack} 默认为null
     * @return 已经开启的 {@link RequestQueue} 实例.
     */
    public static RequestQueue newRequestQueue(Context context, HttpStack stack)
    {
    	return newRequestQueue(context, stack, -1);
    }
    
    /**
     * 方法重载，懒得写了
     *
     * @param context 用户创建缓存目录的 {@link Context}.
     * @return 已经开启的 {@link RequestQueue} 实例.
     */
    public static RequestQueue newRequestQueue(Context context) {
        return newRequestQueue(context, null);
    }
}

