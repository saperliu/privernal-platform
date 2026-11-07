/*
 * Copyright 2025-2026 saperliu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.privernal.dynamic.dbs.util.scan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.util.SystemPropertyUtils;

import java.io.IOException;
import java.util.*;

/**
 * @author saperliu
 * @version 1.0.0
 * @date 2019-08-22 11:26
 * @Description
 */
public class ClassPathScanner {

    private final static Logger LOGGER = LoggerFactory.getLogger(ClassPathScanner.class);

    private static final String DEFAULT_RESOURCE_PATTERN = "**/*.class";
    private ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
    private MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(this.resourcePatternResolver);
    private String resourcePattern = DEFAULT_RESOURCE_PATTERN;
    private final List<TypeFilter> includeFilters = new LinkedList<TypeFilter>();
    private final List<TypeFilter> excludeFilters = new LinkedList<TypeFilter>();

    public void addIncludeFilter(TypeFilter includeFilter) {
        this.includeFilters.add(includeFilter);
    }

    /**
     * Add an exclude type filter to the <i>front</i> of the exclusion list.
     */
    public void addExcludeFilter(TypeFilter excludeFilter) {
        this.excludeFilters.add(0, excludeFilter);
    }

    /**
     * Reset the configured type filters.
     */
    public void resetFilters() {
        this.includeFilters.clear();
        this.excludeFilters.clear();
    }

    /**
     * 将.包路径解释为/路径
     * Resolve the specified base package into a pattern specification for the package search path.
     *
     * @param basePackage
     * @return
     */
    public String packageToRelativedPath(String basePackage) {
        return ClassUtils.convertClassNameToResourcePath(SystemPropertyUtils.resolvePlaceholders(basePackage));
    }

    /**
     * 和配置的排除过滤器和包含过滤器进行匹配过滤
     *
     * @param metadataReader
     * @return
     * @throws IOException
     */
    protected boolean isCandidateComponent(MetadataReader metadataReader) throws IOException {
        for (TypeFilter tf : this.excludeFilters) {
            if (tf.match(metadataReader, this.metadataReaderFactory)) {
                return false;
            }
        }
        for (TypeFilter tf : this.includeFilters) {
            if (tf.match(metadataReader, this.metadataReaderFactory)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 扫描类路径并根据过滤器以找出合条件的类元数据
     *
     * @param basePackage
     * @return
     */
    public Set<MetadataReader> findCandidateClasss(String... basePackage) {
        Set<MetadataReader> candidates = new LinkedHashSet<MetadataReader>();
        try {
            for (String s : basePackage) {
                String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + packageToRelativedPath(s) + "/"
                        + this.resourcePattern;
                LOGGER.info("扫描包: {}", packageSearchPath);
                LOGGER.info("扫描包: classpath {}", this.getClass().getResource("/"));
                Resource[] resources = this.resourcePatternResolver.getResources(packageSearchPath);
                for (Resource resource : resources) {
                    if (resource.isReadable()) {
                        try {
                            MetadataReader metadataReader = this.metadataReaderFactory.getMetadataReader(resource);
//                            if (isCandidateComponent(metadataReader)) {
                            candidates.add(metadataReader);
                            LOGGER.info("匹配: {}", metadataReader.getClassMetadata().getClassName());
//                            }
                        } catch (Throwable ex) {
                            LOGGER.error("类符合条件但读取失败: {}", resource, ex);
                        }
                    } else {
                        LOGGER.info("因为扫描到的类无法读取元数据,忽略: {}", resource);
                    }
                }
            }
        } catch (IOException ex) {
            LOGGER.error("扫描时发生 I/O 异常 :", ex);
        }
        return candidates;
    }


    /**
     * 扫描类路径并根据过滤器以找出合条件的类元数据
     *
     * @param basePackage
     * @return
     */
    public Set<MetadataReader> findCandidateOutClasss(String path, String... basePackage) {
        Set<MetadataReader> candidates = new LinkedHashSet<MetadataReader>();
        try {
            for (String s : basePackage) {
                String packageSearchPath = path + packageToRelativedPath(s) + "/" + this.resourcePattern;
                LOGGER.info("扫描包: {}", packageSearchPath);
                Resource[] resources = this.resourcePatternResolver.getResources(packageSearchPath);
                for (Resource resource : resources) {
                    if (resource.isReadable()) {
                        try {
                            MetadataReader metadataReader = this.metadataReaderFactory.getMetadataReader(resource);
                            if (isCandidateComponent(metadataReader)) {
                                candidates.add(metadataReader);
                                LOGGER.info("匹配: {}", metadataReader.getClassMetadata().getClassName());
                            }
                        } catch (Throwable ex) {
                            LOGGER.error("类符合条件但读取失败: {}", resource, ex);
                        }
                    } else {
                        LOGGER.info("因为扫描到的类无法读取元数据,忽略: {}", resource);
                    }
                }
            }
        } catch (IOException ex) {
            LOGGER.error("扫描时发生 I/O 异常 :", ex);
        }
        return candidates;
    }

    /**
     * 扫描CLASSPATH路径以找出合条件的文件
     *
     * @param patterns
     * @param basePackage
     * @return
     */
    public List<Resource> findCandidateFiles(String patterns, String... basePackage) {
        List<Resource> resources = new LinkedList<Resource>();
        try {
            for (String p : basePackage) {
                String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + packageToRelativedPath(p) + "/" + patterns;
                Resource[] current = this.resourcePatternResolver.getResources(packageSearchPath);
                if (current.length == 0) {
                    continue;
                }
                resources.addAll(Arrays.asList(current));
            }
            return resources;
        } catch (IOException ex) {
            // throw new IOException("扫描时发生 I/O 异常", ex);
            return null;
        }
    }
}
