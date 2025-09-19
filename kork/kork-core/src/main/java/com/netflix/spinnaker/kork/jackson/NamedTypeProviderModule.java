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

package com.netflix.spinnaker.kork.jackson;

import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Jackson module to register {@link NamedType} data discovered from {@link NamedTypeProvider}
 * beans. When using {@link
 * org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration}, all {@link
 * com.fasterxml.jackson.databind.Module} beans are registered in the {@link
 * com.fasterxml.jackson.databind.ObjectMapper} created by {@link
 * org.springframework.http.converter.json.Jackson2ObjectMapperBuilder}.
 */
@Component
public class NamedTypeProviderModule extends SimpleModule {
  public NamedTypeProviderModule(ObjectProvider<NamedTypeProvider> providers) {
    super(NamedTypeProviderModule.class.getSimpleName());
    var namedTypes =
        providers
            .orderedStream()
            .flatMap(provider -> provider.getNamedTypes().stream())
            .toArray(NamedType[]::new);
    registerSubtypes(namedTypes);
  }
}
