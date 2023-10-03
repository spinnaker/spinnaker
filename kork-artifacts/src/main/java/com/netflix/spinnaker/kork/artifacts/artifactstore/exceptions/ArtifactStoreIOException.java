/*
 * Copyright 2023 Apple Inc.
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
package com.netflix.spinnaker.kork.artifacts.artifactstore.exceptions;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * An exception used by the artifact store (de)serializers since the (de)serialize methods only
 * throw IOExceptions, and if any other exception is thrown jackson assumes it's some JSON error.
 */
public class ArtifactStoreIOException extends IOException {
  public ArtifactStoreIOException(Exception e) {
    super(e);
  }

  /**
   * Helper methods to catch any exception thrown by a method and instead throw an
   * ArtifactStoreIOException
   */
  public static <T> T throwIOException(Supplier<T> fn) throws IOException {
    try {
      return fn.get();
    } catch (Exception e) {
      throw new ArtifactStoreIOException(e);
    }
  }
}
