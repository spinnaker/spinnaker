/*
 * Copyright 2026 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.fiat.model.resources;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads a JSON array of extension resource views as {@link GenericAuthorizable}s, since {@link
 * Authorizable} itself is an interface Jackson can't instantiate.
 *
 * <p>This is applied to the specific field rather than to {@link Authorizable} so that the view
 * classes implementing it keep deserializing as themselves.
 */
public class AuthorizableSetDeserializer extends StdDeserializer<Set<Authorizable>> {
  public AuthorizableSetDeserializer() {
    super(Set.class);
  }

  @Override
  public Set<Authorizable> deserialize(JsonParser parser, DeserializationContext context)
      throws IOException {
    JavaType listType =
        context.getTypeFactory().constructCollectionType(List.class, GenericAuthorizable.class);
    List<GenericAuthorizable> values = context.readValue(parser, listType);
    return new HashSet<>(values);
  }
}
