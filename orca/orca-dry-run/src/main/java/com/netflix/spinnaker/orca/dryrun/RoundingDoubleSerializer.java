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

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Serializes a double as an integer if possible.
 *
 * <p>Written in Java as Kotlin's Double doesn't work with Jackson at runtime.
 */
public class RoundingDoubleSerializer extends ValueSerializer<Double> {
  @Override
  public Class<Double> handledType() {
    return Double.class;
  }

  @Override
  public void serialize(Double value, JsonGenerator gen, SerializationContext serializers)
      throws JacksonException {
    if (value % 1 == 0d) {
      gen.writeNumber(value.intValue());
    } else {
      gen.writeNumber(value);
    }
  }
}
