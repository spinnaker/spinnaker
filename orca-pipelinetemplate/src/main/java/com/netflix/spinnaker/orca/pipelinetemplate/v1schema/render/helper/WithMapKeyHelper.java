/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.helper;

import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.Options.Buffer;

import java.io.IOException;
import java.util.Map;

import static org.apache.commons.lang3.Validate.notNull;

public class WithMapKeyHelper implements Helper<Map<String, Object>> {

  @Override
  public Object apply(Map<String, Object> context, Options options) throws IOException {
    notNull(context, "withMapKey: Map value must not be null");

    String key = options.param(0);
    notNull(key, "withMapKey: A key is required");

    if (!context.containsKey(key)) {
      throw new IllegalArgumentException("withObjectKey helper given key that does not exist (key: " + key + ")");
    }

    Object val = context.get(key);

    Buffer buffer = options.buffer();
    if (options.isFalsy(val)) {
      buffer.append(options.inverse(val));
    } else {
      buffer.append(options.fn(val));
    }
    return buffer;
  }
}
