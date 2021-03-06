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

package cn.com.infosec.volley;

/**
 * 执行请求的接口.
 */
public interface Network {
    /**
     * 执行指定的请求.
     * @param request 要被执行的Request
     * @return 一个存储了数据和缓存元数据的 {@link NetworkResponse} 对象; 不会为空
     * @throws VolleyError on errors
     */
    NetworkResponse performRequest(Request<?> request) throws VolleyError;
}
