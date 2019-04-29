/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.kork.jackson;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;

/**
 * Handles discovery and registration of ObjectMapper subtypes.
 *
 * <p>Using the NAME JsonTypeInfo strategy, each subtype needs to be defined explicitly. If all
 * subtypes are known at compile time, the subtypes should be defined on the root type. However,
 * Spinnaker often times offers addition of new types, which may require scanning different packages
 * for additional subtypes. This class will assist this specific task.
 */
public class ObjectMapperSubtypeConfigurer {

  private static final Logger log = LoggerFactory.getLogger(ObjectMapperSubtypeConfigurer.class);

  private boolean strictSerialization;

  public ObjectMapperSubtypeConfigurer(boolean strictSerialization) {
    this.strictSerialization = strictSerialization;
  }

  public void registerSubtypes(ObjectMapper mapper, List<SubtypeLocator> subtypeLocators) {
    subtypeLocators.forEach(locator -> registerSubtype(mapper, locator));
  }

  public void registerSubtype(ObjectMapper mapper, SubtypeLocator subtypeLocator) {
    subtypeLocator
        .searchPackages()
        .forEach(pkg -> mapper.registerSubtypes(findSubtypes(subtypeLocator.rootType(), pkg)));
  }

  private NamedType[] findSubtypes(Class<?> clazz, String pkg) {
    ClassPathScanningCandidateComponentProvider provider =
        new ClassPathScanningCandidateComponentProvider(false);
    provider.addIncludeFilter(new AssignableTypeFilter(clazz));

    return provider.findCandidateComponents(pkg).stream()
        .map(
            bean -> {
              Class<?> cls =
                  ClassUtils.resolveClassName(
                      bean.getBeanClassName(), ClassUtils.getDefaultClassLoader());

              JsonTypeName nameAnnotation = cls.getAnnotation(JsonTypeName.class);
              if (nameAnnotation == null || "".equals(nameAnnotation.value())) {
                String message =
                    "Subtype " + cls.getSimpleName() + " does not have a JsonTypeName annotation";
                if (strictSerialization) {
                  throw new InvalidSubtypeConfigurationException(message);
                }
                log.warn(message);
                return null;
              }

              return new NamedType(cls, nameAnnotation.value());
            })
        .filter(Objects::nonNull)
        .toArray(NamedType[]::new);
  }

  /**
   * Allows configuring subtypes either programmatically with Class or configuration-backed strings.
   */
  public interface SubtypeLocator {
    Class<?> rootType();

    List<String> searchPackages();
  }

  public static class ClassSubtypeLocator implements SubtypeLocator {

    private final Class<?> rootType;
    private final List<String> searchPackages;

    public ClassSubtypeLocator(Class<?> rootType, List<String> searchPackages) {
      this.rootType = rootType;
      this.searchPackages = searchPackages;
    }

    @Override
    public Class<?> rootType() {
      return rootType;
    }

    @Override
    public List<String> searchPackages() {
      return searchPackages;
    }
  }

  public static class StringSubtypeLocator implements SubtypeLocator {

    private final String rootTypeName;
    private final List<String> searchPackages;

    public StringSubtypeLocator(String rootTypeName, List<String> searchPackages) {
      this.rootTypeName = rootTypeName;
      this.searchPackages = searchPackages;
    }

    @Override
    public Class<?> rootType() {
      return ClassUtils.resolveClassName(rootTypeName, ClassUtils.getDefaultClassLoader());
    }

    @Override
    public List<String> searchPackages() {
      return searchPackages;
    }
  }
}
