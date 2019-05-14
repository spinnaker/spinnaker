/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.dryrun;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

/**
 * Serializes a float as an integer if possible.
 *
 * <p>Written in Java as Kotlin's Float doesn't work with Jackson at runtime.
 */
public class RoundingFloatSerializer extends JsonSerializer<Float> {
  @Override
  public Class<Float> handledType() {
    return Float.class;
  }

  @Override
  public void serialize(Float value, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
    if (value % 1 == 0f) {
      gen.writeNumber(value.intValue());
    } else {
      gen.writeNumber(value);
    }
  }
}
