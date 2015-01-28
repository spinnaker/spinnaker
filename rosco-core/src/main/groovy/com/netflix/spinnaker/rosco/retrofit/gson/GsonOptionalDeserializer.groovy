/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.rosco.retrofit.gson

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import groovy.transform.CompileStatic
import com.google.common.base.Optional
import com.google.gson.*

@CompileStatic
class GsonOptionalDeserializer<T> implements JsonSerializer<Optional<T>>, JsonDeserializer<Optional<T>> {

  @Override
  public Optional<T> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
    final T value = context.deserialize(json, ((ParameterizedType) typeOfT).actualTypeArguments[0])
    return Optional.fromNullable(value)
  }

  @Override
  public JsonElement serialize(Optional<T> src, Type typeOfSrc, JsonSerializationContext context) {
    context.serialize(src.orNull(), ((ParameterizedType) typeOfSrc).actualTypeArguments[0])
  }
}
