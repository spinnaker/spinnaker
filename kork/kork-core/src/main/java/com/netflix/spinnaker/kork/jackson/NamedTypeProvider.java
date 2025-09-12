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
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint;
import java.util.List;

/**
 * Bean for providing {@link NamedType} data for type discriminators. When combined with {@link
 * NamedTypeAutoConfiguration}, beans of this type will register named subtypes to Jackson. These
 * named types will be automatically registered with {@code ObjectMapper} instances created via
 * {@link org.springframework.http.converter.json.Jackson2ObjectMapperBuilder}.
 */
@FunctionalInterface
public interface NamedTypeProvider extends SpinnakerExtensionPoint {
  List<NamedType> getNamedTypes();
}
