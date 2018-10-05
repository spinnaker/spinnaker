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
package com.netflix.spinnaker.front50.graphql;

import graphql.schema.Coercing;
import graphql.schema.GraphQLScalarType;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Based on https://github.com/taion/graphql-type-json
 */
public class JsonScalarType extends GraphQLScalarType {

  public final static JsonScalarType INSTANCE = new JsonScalarType();

  private final static String NAME = "JSON";
  private final static String DESCRIPTION = "The `JSON` scalar type represents JSON values as specified by " +
    "[ECMA-404](http://www.ecma-international.org/ " +
    "publications/files/ECMA-ST/ECMA-404.pdf).";

  private JsonScalarType() {
    super(NAME, DESCRIPTION, new Coercing() {
      @Override
      public Object serialize(Object dataFetcherResult) {
        return dataFetcherResult;
      }

      @Override
      public Object parseValue(Object input) {
        return input;
      }

      @Override
      public Object parseLiteral(Object input) {
        if (input == null) {
          return null;
        }
        if (input instanceof String || input instanceof Boolean) {
          return input;
        }
        if (input instanceof Number) {
          return ((Number) input).floatValue();
        }
        if (input instanceof Map) {
          HashMap<Object, Object> map = new HashMap<>();
          for (Map.Entry<?, ?> entry : ((Map<?, ?>) input).entrySet()) {
            map.put(entry.getKey(), parseLiteral(entry.getValue()));
          }
          return map;
        }
        if (input instanceof Collection) {
          return ((Collection<?>) input).stream().map(this::parseLiteral).collect(Collectors.toList());
        }
        return input;
      }
    });
  }
}
