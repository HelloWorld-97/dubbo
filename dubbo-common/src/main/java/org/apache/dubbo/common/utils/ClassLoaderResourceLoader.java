/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.common.utils;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ClassLoaderResourceLoader {
    private final static ExecutorService executorService =
        new ThreadPoolExecutor(0, Integer.MAX_VALUE,
            60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new NamedThreadFactory("DubboClassLoaderResourceLoader", true));

    private static SoftReference<Map<ClassLoader, Map<String, Set<URL>>>> classLoaderResourcesCache = null;

    public static Map<ClassLoader, Set<java.net.URL>> loadResources(String fileName, List<ClassLoader> classLoaders) {
        Map<ClassLoader, Set<java.net.URL>> resources = new ConcurrentHashMap<>();
        CountDownLatch countDownLatch = new CountDownLatch(classLoaders.size());
        for (ClassLoader classLoader : classLoaders) {
            executorService.submit(()->{
                resources.put(classLoader, loadResources(fileName, classLoader));
                countDownLatch.countDown();
            });
        }
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(resources));
    }

    public static Set<java.net.URL> loadResources(String fileName, ClassLoader currentClassLoader) {
        Map<ClassLoader, Map<String, Set<java.net.URL>>> classLoaderCache;
        if (classLoaderResourcesCache == null || (classLoaderCache = classLoaderResourcesCache.get()) == null) {
            synchronized (ConfigUtils.class) {
                if (classLoaderResourcesCache == null || (classLoaderCache = classLoaderResourcesCache.get()) == null) {
                    classLoaderCache = new ConcurrentHashMap<>();
                    classLoaderResourcesCache = new SoftReference<>(classLoaderCache);
                }
            }
        }
        if (!classLoaderCache.containsKey(currentClassLoader)) {
            classLoaderCache.putIfAbsent(currentClassLoader, new ConcurrentHashMap<>());
        }
        Map<String, Set<java.net.URL>> urlCache = classLoaderCache.get(currentClassLoader);
        if (!urlCache.containsKey(fileName)) {
            Set<java.net.URL> set = new LinkedHashSet<>();
            Enumeration<URL> urls = null;
            try {
                urls = currentClassLoader.getResources(fileName);
                if (urls != null) {
                    while (urls.hasMoreElements()) {
                        set.add(urls.nextElement());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            urlCache.put(fileName, set);
        }
        return urlCache.get(fileName);
    }


}
