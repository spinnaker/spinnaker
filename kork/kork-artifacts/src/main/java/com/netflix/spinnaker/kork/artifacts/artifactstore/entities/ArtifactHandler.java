/*
 * Copyright 2025 Apple Inc.
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
package com.netflix.spinnaker.kork.artifacts.artifactstore.entities;

/**
 * Common handler interfaces that are used to group the various handler types.
 *
 * <p>Classes that are implementing a handler should not interface this class directly, but instead
 * interface either the ArtifactStorageHandler or ArtifactStoragePropertyHandler. Otherwise, a noop
 * would occur.
 *
 * <p>Further, this interface follows the handler pattern which can allow for some customization on
 * how a custom Spinnaker instance may want to address certain keys in an execution context. For
 * example, we may have a simple JSON of
 *
 * <pre>
 *     {
 *         "foo": {
 *             "bar": [0,1,2],
 *             "baz": "some-string"
 *         },
 *         "qux": [
 *              {
 *                  "field1": "value1",
 *                  "field2": "value2",
 *              }
 *         ]
 *     }
 * </pre>
 *
 * A custom handler can be used to match against criteria of an element with the hook it is
 * associated with. Below illustrates a simple custom artifact storage handler that replaces the
 * value stored in key 'qux' with a new object.
 *
 * <pre>
 *     public class QuxHandler implements ArtifactStorageHandler {
 *       public boolean canHandle(Object v) {
 *          return v instanceof Map && ((Map) v).get("qux") != null;
 *       }
 *
 *       public <V> V handle(ArtifactStore store, V v, ObjectMapper objectMapper) {
 *          Map<?, ?> m = (Map) v;
 *          m.put("qux", Map.of("newField1", newField2"));
 *          return m;
 *       }
 *     }
 * </pre>
 *
 * We can then register this handler in the EntityStoreConfiguration:
 *
 * <pre>
 *     return ArtifactHandlerLists.builder()
 *     .mapHandlers(new QuxHandler()))
 *     .build();
 * </pre>
 */
public interface ArtifactHandler {}
