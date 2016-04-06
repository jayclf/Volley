/*
 * Copyright (C) 2012 The Android Open Source Project
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * ByteArrayPool 是一个 <code>byte[]</code> 对象仓库源. 它的目的是向消费者提供一种短时间使用然后就处理掉的的缓冲区.
 * 在Android设备上简单那的创建和处理这样的缓存会引起相当大的堆绕动和垃圾回收延迟, 缺少一个短生命周期堆对象的良好管理.
 * 这可能有利于用永久性的缓冲池的形式来交换一些内存，以便使堆性能得到改善；这就是我们这个类要做的事情.
 * <p>
 * 这个类适用于那些在IO中使用大量的临时<code>byte[]</code> 缓冲区来copy数据.
 * 在这种使用状况下, 用户通常想要一个稍微小一点的缓冲区以便保证更好的性能 (例如. 当通过流的方式拷贝数据时), 但不介意如果缓冲大于最小值.
 * 以此，也最大限度地能够重用一个循环缓冲区的可能性，该类释放的缓存大于要求的大小
 * 调用者可以优雅的处理任何小于最小值的缓冲区.
 * <p>
 * 当在缓冲区中没有一个合适大小的缓存提供给缓存请求的时候,这个类会分配一个新的缓存并返回.
 * <p>
 * 该类对于它所创键的buffer没有特殊的所有权; 调用者可以很自由的从缓存池中拿出一个buffer,可以永久的使用,并且可以永远返回给缓冲池;
 * 另外,在任何地方分配的buffer返回到这个缓冲池都是无害的,只要没有其他的干扰的引用.
 * <p>
 * 该类保证这些回收池中buffer的大小永远不会超过一个确切的限制. 如果一个buffer的返回会引发缓冲池的大小超过限制,最少被使用的buffer将会被丢弃.
 */
public class ByteArrayPool {
    /** 缓冲池, 按最后使用和缓冲大小排列 */
    private List<byte[]> mBuffersByLastUse = new LinkedList<byte[]>();
    private List<byte[]> mBuffersBySize = new ArrayList<byte[]>(64);

    /** 当前缓冲池总大小 */
    private int mCurrentSize = 0;

    /**
     * 缓冲池总计最大的尺寸. 超过限制的旧的buffer会被丢弃.
     */
    private final int mSizeLimit;

    /** 以尺寸来比较buffer */
    protected static final Comparator<byte[]> BUF_COMPARATOR = new Comparator<byte[]>() {
        @Override
        public int compare(byte[] lhs, byte[] rhs) {
            return lhs.length - rhs.length;
        }
    };

    /**
     * @param sizeLimit 缓冲池最大的大小,单位是bytes
     */
    public ByteArrayPool(int sizeLimit) {
        mSizeLimit = sizeLimit;
    }

    /**
     * 如果请求的尺寸在缓冲池中有可用的的则直接从缓存池中返回buffer，否则就创建一个新的返回.
     *
     * @param len 请求buffer的最小尺寸,单位是bytes. 返回的buffer可能回稍微大一点.
     * @return 总是返回一个 byte[] 的buffer.
     */
    public synchronized byte[] getBuf(int len) {
        for (int i = 0; i < mBuffersBySize.size(); i++) {
            byte[] buf = mBuffersBySize.get(i);
            if (buf.length >= len) {
                mCurrentSize -= buf.length;
                mBuffersBySize.remove(i);
                mBuffersByLastUse.remove(buf);
                return buf;
            }
        }
        return new byte[len];
    }

    /**
     * 把一个buffer还给缓冲池, 如果超过尺寸限制就丢弃旧的缓存池.
     * @param buf 要归还给pool的buffer.
     */
    public synchronized void returnBuf(byte[] buf) {
        if (buf == null || buf.length > mSizeLimit) {
            return;
        }
        mBuffersByLastUse.add(buf);
        int pos = Collections.binarySearch(mBuffersBySize, buf, BUF_COMPARATOR);
        if (pos < 0) {
            pos = -pos - 1;
        }
        mBuffersBySize.add(pos, buf);
        mCurrentSize += buf.length;
        trim();
    }

    /**
     * 从缓存池中删除buffer，直到他的尺寸在限制以下.
     */
    private synchronized void trim() {
        while (mCurrentSize > mSizeLimit) {
            byte[] buf = mBuffersByLastUse.remove(0);
            mBuffersBySize.remove(buf);
            mCurrentSize -= buf.length;
        }
    }

}
