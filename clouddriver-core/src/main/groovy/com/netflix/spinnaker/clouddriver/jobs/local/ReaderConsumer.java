/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.jobs.local;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * Transforms a stream into an object of arbitrary type using a supplied BufferReader for the
 * stream.
 *
 * <p>Implementations are responsible for closing the supplied BufferReader.
 */
public interface ReaderConsumer<T> {
  T consume(BufferedReader r) throws IOException;
}
