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

package com.android.volley;

import java.util.Collections;
import java.util.Map;

/**
 * 该接口用于以键值对的形式缓存byte数组,键是一个String
 */
public interface Cache {
    /**
     * 返回一个空的Entry.
     * @param key key
     * @return 一个 {@link Entry} or null
     */
    Entry get(String key);

    /**
     * 添加或者替换entry到cache.
     * @param key key
     * @param entry 要存储的Entry
     */
    void put(String key, Entry entry);

    /**
     * 执行任何具有潜在长期运行特性的动作之前,必须进行初始化;
     * 该方法运行在子线程.
     */
    void initialize();

    /**
     * 刷新Cache中的Entry.
     * @param key key
     * @param fullExpire 该Entry是否完整过期
     */
    void invalidate(String key, boolean fullExpire);

    /**
     * 从cache中移除一个entry.
     * @param key Cache key
     */
    void remove(String key);

    /**
     * 清空cache.
     */
    void clear();

    /**
     * 一个entry的数据和主数据实体bean.
     */
    class Entry {
        /** 数据区域. */
        public byte[] data;

        /** cache数据的Etag. */
        public String etag;

        /** 服务器响应的时间. */
        public long serverDate;

        /** 资源最后编辑时间. */
        public long lastModified;

        /** record 的ttl. */
        public long ttl;

        /** record 的软ttl. */
        public long softTtl;

        /** 从服务器接收的固定的响应头; 不能为null. */
        public Map<String, String> responseHeaders = Collections.emptyMap();

        /** 判断该资源是否超时. */
        public boolean isExpired() {
            return this.ttl < System.currentTimeMillis();
        }

        /** 返回True如果需要从原数据上刷新. */
        public boolean refreshNeeded() {
            return this.softTtl < System.currentTimeMillis();
        }
    }

}
