/*
 * Copyright 2020 Netflix, Inc.
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
 */

package com.netflix.spinnaker.kork.version;

import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

/**
 * Resolves the version by finding the Spring bean annotated with {@link
 * org.springframework.boot.autoconfigure.SpringBootApplication} and then determining the
 * Implementation-Version attribute from the package.
 *
 * <p>See {@link java.lang.Package#getImplementationVersion()}
 */
public class SpringPackageVersionResolver implements VersionResolver {

  ApplicationContext applicationContext;

  public SpringPackageVersionResolver(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  @Nullable
  @Override
  public String resolve(@Nonnull String serviceName) {
    Map<String, Object> annotatedBeans =
        applicationContext.getBeansWithAnnotation(SpringBootApplication.class);
    return annotatedBeans.isEmpty()
        ? null
        : annotatedBeans.values().toArray()[0].getClass().getPackage().getImplementationVersion();
  }

  @Override
  public int getOrder() {
    return 0;
  }
}
