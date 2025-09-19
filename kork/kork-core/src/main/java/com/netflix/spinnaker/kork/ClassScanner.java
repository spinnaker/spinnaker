/*
 * Copyright 2024 Apple, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.kork;

import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.ClassUtils;

/**
 * Utility to scan the class path for classes implementing a base type.
 *
 * @param <T> base type scanned classes must implement
 */
@Log4j2
@NonnullByDefault
public class ClassScanner<T> {

  /** Creates a new class scanner using the provided base type. */
  public static <T> ClassScanner<T> forBaseType(Class<T> baseType) {
    return new ClassScanner<>(baseType);
  }

  private final Class<T> baseType;
  private final Counter unloadableClasses;
  private final ClassPathScanningCandidateComponentProvider provider =
      new ClassPathScanningCandidateComponentProvider(false);
  private final Map<ResourceLoader, Set<String>> loadablePackageSets = new IdentityHashMap<>();
  private final ResourceLoader defaultResourceLoader = new DefaultResourceLoader();

  private ClassScanner(Class<T> baseType) {
    this.baseType = baseType;
    unloadableClasses =
        Metrics.counter(
            "class.scanner.unloadable", Tags.of(Tag.of("baseType", baseType.getName())));
    provider.addIncludeFilter(new AssignableTypeFilter(baseType));
  }

  /** Adds the given package name to the set of packages to scan using a default resource loader. */
  public ClassScanner<T> addLoadablePackage(String packageName) {
    return addLoadablePackage(defaultResourceLoader, packageName);
  }

  /** Adds the given package name to the set of packages to scan using the given resource loader. */
  public ClassScanner<T> addLoadablePackage(ResourceLoader resourceLoader, String packageName) {
    loadablePackageSets
        .computeIfAbsent(resourceLoader, k -> new LinkedHashSet<>())
        .add(packageName);
    return this;
  }

  /**
   * Adds a collection of package names to the set of packages to scan using the given resource
   * loader.
   */
  public ClassScanner<T> addLoadablePackages(
      ResourceLoader resourceLoader, Collection<String> packageNames) {
    loadablePackageSets
        .computeIfAbsent(resourceLoader, k -> new LinkedHashSet<>())
        .addAll(packageNames);
    return this;
  }

  /** Adds all defined packages from the given class loader to be scanned. */
  public ClassScanner<T> addClassLoaderDefinedPackages(ClassLoader classLoader) {
    var resourceLoader = new DefaultResourceLoader(classLoader);
    var packageNames =
        Stream.of(classLoader.getDefinedPackages())
            .map(Package::getName)
            .collect(Collectors.toSet());
    return addLoadablePackages(resourceLoader, packageNames);
  }

  /** Adds the containing package of the given class to the set of packages to be scanned. */
  public ClassScanner<T> addClassPackage(Class<?> classFromPackage) {
    var resourceLoader = new DefaultResourceLoader(classFromPackage.getClassLoader());
    var packageName = classFromPackage.getPackageName();
    return addLoadablePackage(resourceLoader, packageName);
  }

  public ClassScanner<T> addIncludeFilter(TypeFilter typeFilter) {
    provider.addIncludeFilter(typeFilter);
    return this;
  }

  public ClassScanner<T> addExcludeFilter(TypeFilter typeFilter) {
    provider.addExcludeFilter(typeFilter);
    return this;
  }

  /**
   * Scans and returns the configured sets of packages and resource loaders for concrete classes
   * matching the configured base type. Note that this does not return abstract classes or
   * interfaces.
   */
  public Set<Class<? extends T>> scan() {
    log.debug("Scanning classes with base type {}", baseType.getName());
    Set<Class<? extends T>> classes = new HashSet<>();
    for (var entry : loadablePackageSets.entrySet()) {
      var resourceLoader = entry.getKey();
      provider.setResourceLoader(resourceLoader);
      var classLoader = resourceLoader.getClassLoader();
      for (var packageName : entry.getValue()) {
        for (var candidateComponent : provider.findCandidateComponents(packageName)) {
          var candidateClassName = candidateComponent.getBeanClassName();
          if (candidateClassName != null) {
            log.debug(
                "Loading candidate class {} for base type {}",
                candidateClassName,
                baseType.getName());
            try {
              var loadedClass = ClassUtils.forName(candidateClassName, classLoader);
              var subclass = loadedClass.asSubclass(baseType);
              log.debug("Loaded class {} for base type {}", subclass, baseType.getName());
              classes.add(subclass);
            } catch (ClassNotFoundException | LinkageError | ClassCastException e) {
              unloadableClasses.increment();
              log.warn(
                  "Failed to load candidate class {} for base type {}",
                  candidateClassName,
                  baseType.getName(),
                  e);
            }
          }
        }
      }
    }
    return classes;
  }
}
