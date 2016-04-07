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

import com.android.volley.Cache;
import com.android.volley.VolleyLog;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文件可以直接缓存到本地硬盘的实现. 默认的大小是5MB,但是可以由我们自己配置.
 */
public class DiskBasedCache implements Cache {

    /**
     * Map的Key, CacheHeader键值对
     * 初始大小16,加载因子0.75,也就是说超过12个元素就会扩容
     * false 基于插入顺序排列元素  true  基于访问顺序排列元素（LRU算法）
     */
    private final Map<String, CacheHeader> mEntries = new LinkedHashMap<String, CacheHeader>(16, .75f, true);

    /** 当前被使用的总的空间大小(bytes). */
    private long mTotalSize = 0;

    /** 用于cache的根目录. */
    private final File mRootDirectory;

    /** 缓存的最大尺寸(bytes). */
    private final int mMaxCacheSizeInBytes;

    /** 默认最大的缓存尺寸(5MB). */
    private static final int DEFAULT_DISK_USAGE_BYTES = 5 * 1024 * 1024;

    /** 缓存中的警报线 */
    private static final float HYSTERESIS_FACTOR = 0.9f;

    /** 当前缓存文件格式的魔法数. */
    private static final int CACHE_MAGIC = 0x20150306;

    /**
     * 构建一个DiskBasedCache的实例，以指定的目录.
     * @param rootDirectory 缓存根目录.
     * @param maxCacheSizeInBytes 缓存最大空间.
     */
    public DiskBasedCache(File rootDirectory, int maxCacheSizeInBytes) {
        mRootDirectory = rootDirectory;
        mMaxCacheSizeInBytes = maxCacheSizeInBytes;
    }

    /**
     * 构建一个DiskBasedCache的实例，以指定的目录，默认缓存大小5MB.
     * @param rootDirectory 缓存根目录.
     */
    public DiskBasedCache(File rootDirectory) {
        this(rootDirectory, DEFAULT_DISK_USAGE_BYTES);
    }

    /**
     * 清除缓存.从磁盘中删除所有缓存文件.
     */
    @Override
    public synchronized void clear() {
        //先删除文件
        File[] files = mRootDirectory.listFiles();
        if (files != null) {
            for (File file : files) {
                VolleyLog.d("deleting cache dir:" + file.getName() + "," + file.delete());
            }
        }
        //再清除对象
        mEntries.clear();
        //当前尺寸置0
        mTotalSize = 0;
        VolleyLog.d("Cache cleared.");
    }

    /**
     * 如果指定的key存在,返回Cache.Entry,否则返回null
     */
    @Override
    public synchronized Entry get(String key) {
        //从封装的Map中取出CacheHeader
        CacheHeader entry = mEntries.get(key);
        // 不存在直接返回null.
        if (entry == null) {
            return null;
        }
        //找出key对应的文件
        File file = getFileForKey(key);
        // CountingInputStream是一个过滤流
        CountingInputStream cis = null;
        try {
            cis = new CountingInputStream(new BufferedInputStream(new FileInputStream(file)));
            // 以过滤的方式从文件中读取一个CacheHeader
            CacheHeader.readHeader(cis);
            // 将读取到的CacheHeader对象转换为byte[]
            byte[] data = streamToBytes(cis, (int) (file.length() - cis.bytesRead));
            // 返回CacheHeader中的Entry
            return entry.toCacheEntry(data);
        } catch (IOException e) {
            VolleyLog.d("%s: %s", file.getAbsolutePath(), e.toString());
            remove(key);
            return null;
        }  catch (NegativeArraySizeException e) {
            VolleyLog.d("%s: %s", file.getAbsolutePath(), e.toString());
            remove(key);
            return null;
        } finally {
            if (cis != null) {
                try {
                    cis.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        }
    }

    /**
     * 初始化DiskBasedCache并扫描指定根目录中的所有文件.
     * 如果根目录不存在就创建出来.
     */
    @Override
    public synchronized void initialize() {
        if (!mRootDirectory.exists()) {
            if (!mRootDirectory.mkdirs()) {
                VolleyLog.e("Unable to create cache dir %s", mRootDirectory.getAbsolutePath());
            }
            // 刚创建的根目录，新的，不用扫描了
            return;
        }
        // 开始扫描
        File[] files = mRootDirectory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            BufferedInputStream fis = null;
            try {
                // 扫描文件中所有的CacheEntry
                fis = new BufferedInputStream(new FileInputStream(file));
                CacheHeader entry = CacheHeader.readHeader(fis);
                entry.size = file.length();
                // 放进成员变量map中
                putEntry(entry.key, entry);
            } catch (IOException e) {
                VolleyLog.d("deleting cache dir:" + file.getName() + "," + file.delete());
            } finally {
                try {
                    if (fis != null) {
                        fis.close();
                    }
                } catch (IOException ignored) { }
            }
        }
    }

    /**
     * 刷新Cache中的Entry.
     * @param key key
     * @param fullExpire 该Entry是否完整过期
     */
    @Override
    public synchronized void invalidate(String key, boolean fullExpire) {
        Entry entry = get(key);
        if (entry != null) {
            entry.softTtl = 0;
            if (fullExpire) {
                entry.ttl = 0;
            }
            put(key, entry);
        }
    }

    /**
     * 把指定的Key和Entry放进Cache中.
     */
    @Override
    public synchronized void put(String key, Entry entry) {
        //修剪当前的Cache，使之能够放进该Entry
        pruneIfNeeded(entry.data.length);
        // 创建该key对应的文件
        File file = getFileForKey(key);
        try {
            // 将CacheHeader写入文件中
            BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(file));
            CacheHeader e = new CacheHeader(key, entry);
            boolean success = e.writeHeader(fos);
            if (!success) {
                //写入失败
                fos.close();
                VolleyLog.d("Failed to write header for %s", file.getAbsolutePath());
                throw new IOException();
            }
            // 写入数据
            fos.write(entry.data);
            fos.close();
            // 添加到当前map
            putEntry(key, e);
            return;
        } catch (IOException e) {
            e.printStackTrace();
        }
        //  ？？为啥要把文件删除了？
        boolean deleted = file.delete();
        if (!deleted) {
            VolleyLog.d("Could not clean up file %s", file.getAbsolutePath());
        }
    }

    /**
     * 从cache中删除元素.
     */
    @Override
    public synchronized void remove(String key) {
        boolean deleted = getFileForKey(key).delete();
        removeEntry(key);
        if (!deleted) {
            VolleyLog.d("Could not delete cache entry for key=%s, filename=%s", key, getFilenameForKey(key));
        }
    }

    /**
     * 为指定的key创建一个伪随机的文件名.
     * 根据算法来判断，一个确定的key随机出来的文件名也是一样的
     * @param key 用于生成文件名的key.
     * @return 一个伪随机文件名.
     */
    private String getFilenameForKey(String key) {
        // 这就是伪随机算法
        int firstHalfLength = key.length() / 2;
        String localFilename = String.valueOf(key.substring(0, firstHalfLength).hashCode());
        localFilename += String.valueOf(key.substring(firstHalfLength).hashCode());
        return localFilename;
    }

    /**
     * 根据Key找出对应的文件.
     */
    public File getFileForKey(String key) {
        return new File(mRootDirectory, getFilenameForKey(key));
    }

    /**
     * 修剪当前的Cache，使之能够腾出指定的空间.
     * LRU算法的一种体现
     * @param neededSpace 要腾出来的空间.
     */
    private void pruneIfNeeded(int neededSpace) {
        if ((mTotalSize + neededSpace) < mMaxCacheSizeInBytes) {
            // 空间富余
            return;
        }
        // 空间不够，需要删除最少使用的元素
        if (VolleyLog.DEBUG) {
            VolleyLog.v("Pruning old cache entries.");
        }
        // 删除之前的空间大小
        long before = mTotalSize;
        // 腾出的文件数
        int prunedFiles = 0;
        // 开始时间
        long startTime = SystemClock.elapsedRealtime();

        Iterator<Map.Entry<String, CacheHeader>> iterator = mEntries.entrySet().iterator();
        while (iterator.hasNext()) {
            // 取出每一个CacheHeader
            Map.Entry<String, CacheHeader> entry = iterator.next();
            CacheHeader e = entry.getValue();
            // 删除CacheHeader对应的文件，不需要判断LRU，因为我们的Map已经按照了LRU排序
            boolean deleted = getFileForKey(e.key).delete();
            if (deleted) {
                // 删除成功，腾出了空间
                mTotalSize -= e.size;
            } else {
               VolleyLog.d("Could not delete cache entry for key=%s, filename=%s", e.key, getFilenameForKey(e.key));
            }
            // 从map中移除被删除的CacheHeader
            iterator.remove();
            // 删除个数加1
            prunedFiles++;
            // 看看空间够了吗，够了就不用再删了
            if ((mTotalSize + neededSpace) < mMaxCacheSizeInBytes * HYSTERESIS_FACTOR) {
                break;
            }
        }

        if (VolleyLog.DEBUG) {
            VolleyLog.v("pruned %d files, %d bytes, %d ms", prunedFiles, (mTotalSize - before), SystemClock.elapsedRealtime() - startTime);
        }
    }

    /**
     * 把CacheHeader放到当前map中.
     * @param key 放入的key.
     * @param entry 要放入的CacheHeader
     */
    private void putEntry(String key, CacheHeader entry) {
        if (!mEntries.containsKey(key)) {
            // 以前没有，直接加上新的CacheHeader的长度
            mTotalSize += entry.size;
        } else {
            // 以前有了，加上两者之差
            CacheHeader oldEntry = mEntries.get(key);
            mTotalSize += (entry.size - oldEntry.size);
        }
        // 放进map
        mEntries.put(key, entry);
    }

    /**
     * 从map中移除指定的CacheHeader.
     */
    private void removeEntry(String key) {
        CacheHeader entry = mEntries.get(key);
        if (entry != null) {
            mTotalSize -= entry.size;
            mEntries.remove(key);
        }
    }

    /**
     * 从流中读取指定长度的 byte[].
     * */
    private static byte[] streamToBytes(InputStream in, int length) throws IOException {
        byte[] bytes = new byte[length];
        int count;
        int pos = 0;
        while (pos < length && ((count = in.read(bytes, pos, length - pos)) != -1)) {
            pos += count;
        }
        if (pos != length) {
            throw new IOException("Expected " + length + " bytes, read " + pos + " bytes");
        }
        return bytes;
    }

    /**
     * entry缓存的value，也就是缓存实体.
     */
    static class CacheHeader {
        /** 被CacheHeader定义的数据大小. (不会序列化到磁盘. */
        public long size;

        /** 标记 cache entry的key. */
        public String key;

        /** cache 相关的ETag. */
        public String etag;

        /** 服务器响应的时间. */
        public long serverDate;

        /** 资源最后编辑时间. */
        public long lastModified;

        /** record 的ttl. */
        public long ttl;

        /** record 的软ttl. */
        public long softTtl;

        /** 存储在cache entry中的响应头信息. */
        public Map<String, String> responseHeaders;
        // 构造
        private CacheHeader() { }

        /**
         * 实例化一个CacheHeader对象
         * @param key 标记cache entry的key
         * @param entry 对应的cache entry.
         */
        public CacheHeader(String key, Entry entry) {
            this.key = key;
            this.size = entry.data.length;
            this.etag = entry.etag;
            this.serverDate = entry.serverDate;
            this.lastModified = entry.lastModified;
            this.ttl = entry.ttl;
            this.softTtl = entry.softTtl;
            this.responseHeaders = entry.responseHeaders;
        }

        /**
         * 从流中读取一个CacheHeader 对象.
         * @param is 要读取的输入流.
         * @throws IOException
         */
        public static CacheHeader readHeader(InputStream is) throws IOException {
            CacheHeader entry = new CacheHeader();
            // 读取并判断魔法数
            int magic = readInt(is);
            if (magic != CACHE_MAGIC) {
                // 不要直接删除，最终会被修剪掉
                throw new IOException();
            }
            entry.key = readString(is);
            entry.etag = readString(is);
            if (entry.etag.equals("")) {
                entry.etag = null;
            }
            entry.serverDate = readLong(is);
            entry.lastModified = readLong(is);
            entry.ttl = readLong(is);
            entry.softTtl = readLong(is);
            entry.responseHeaders = readStringStringMap(is);
            return entry;
        }

        /**
         * 以指定的数据中创建一个cache entry.
         */
        public Entry toCacheEntry(byte[] data) {
            Entry e = new Entry();
            e.data = data;
            e.etag = etag;
            e.serverDate = serverDate;
            e.lastModified = lastModified;
            e.ttl = ttl;
            e.softTtl = softTtl;
            e.responseHeaders = responseHeaders;
            return e;
        }


        /**
         * 把CacheHeader写出到指定的输出流.
         */
        public boolean writeHeader(OutputStream os) {
            try {
                writeInt(os, CACHE_MAGIC);
                writeString(os, key);
                writeString(os, etag == null ? "" : etag);
                writeLong(os, serverDate);
                writeLong(os, lastModified);
                writeLong(os, ttl);
                writeLong(os, softTtl);
                writeStringStringMap(responseHeaders, os);
                os.flush();
                return true;
            } catch (IOException e) {
                VolleyLog.d("%s", e.toString());
                return false;
            }
        }

    }
    // FilterInputStream流的子类，过滤读取内容
    private static class CountingInputStream extends FilterInputStream {
        private int bytesRead = 0;

        private CountingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int result = super.read();
            if (result != -1) {
                bytesRead++;
            }
            return result;
        }

        @Override
        public int read(byte[] buffer, int offset, int count) throws IOException {
            int result = super.read(buffer, offset, count);
            if (result != -1) {
                bytesRead += result;
            }
            return result;
        }
    }

    /*
     * 自创的简单的序列化系统用于读取和写入磁盘上的CacheHeader.
     * 以前我们使用的是标准的Java对象流, 但是对反射的严重依赖(甚至是标准类型)导致产生了一大堆垃圾.
     */

    /**
     * 简单的包装了 {@link InputStream#read()}
     * 如果读取完毕会抛出EOFException而不是返回-1.
     */
    private static int read(InputStream is) throws IOException {
        int b = is.read();
        if (b == -1) {
            // 读到头了
            throw new EOFException();
        }
        return b;
    }

    static void writeInt(OutputStream os, int n) throws IOException {
        os.write((n) & 0xff);
        os.write((n >> 8) & 0xff);
        os.write((n >> 16) & 0xff);
        os.write((n >> 24) & 0xff);
    }

    static int readInt(InputStream is) throws IOException {
        int n = 0;
        n |= (read(is));
        n |= (read(is) << 8);
        n |= (read(is) << 16);
        n |= (read(is) << 24);
        return n;
    }

    static void writeLong(OutputStream os, long n) throws IOException {
        os.write((byte)(n));
        os.write((byte)(n >>> 8));
        os.write((byte)(n >>> 16));
        os.write((byte)(n >>> 24));
        os.write((byte)(n >>> 32));
        os.write((byte)(n >>> 40));
        os.write((byte)(n >>> 48));
        os.write((byte)(n >>> 56));
    }

    static long readLong(InputStream is) throws IOException {
        long n = 0;
        n |= ((read(is) & 0xFFL));
        n |= ((read(is) & 0xFFL) << 8);
        n |= ((read(is) & 0xFFL) << 16);
        n |= ((read(is) & 0xFFL) << 24);
        n |= ((read(is) & 0xFFL) << 32);
        n |= ((read(is) & 0xFFL) << 40);
        n |= ((read(is) & 0xFFL) << 48);
        n |= ((read(is) & 0xFFL) << 56);
        return n;
    }

    static void writeString(OutputStream os, String s) throws IOException {
        byte[] b = s.getBytes("UTF-8");
        writeLong(os, b.length);
        os.write(b, 0, b.length);
    }

    static String readString(InputStream is) throws IOException {
        int n = (int) readLong(is);
        byte[] b = streamToBytes(is, n);
        return new String(b, "UTF-8");
    }

    static void writeStringStringMap(Map<String, String> map, OutputStream os) throws IOException {
        if (map != null) {
            writeInt(os, map.size());
            for (Map.Entry<String, String> entry : map.entrySet()) {
                writeString(os, entry.getKey());
                writeString(os, entry.getValue());
            }
        } else {
            writeInt(os, 0);
        }
    }

    static Map<String, String> readStringStringMap(InputStream is) throws IOException {
        int size = readInt(is);
        Map<String, String> result = (size == 0)
                ? Collections.<String, String>emptyMap()
                : new HashMap<String, String>(size);
        for (int i = 0; i < size; i++) {
            String key = readString(is).intern();
            String value = readString(is).intern();
            result.put(key, value);
        }
        return result;
    }


}
