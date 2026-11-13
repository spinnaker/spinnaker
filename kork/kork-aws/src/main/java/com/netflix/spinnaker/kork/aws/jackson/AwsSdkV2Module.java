/*
 * Copyright 2026 Harness, Inc.
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

package com.netflix.spinnaker.kork.aws.jackson;

/**
 * Jackson 3 module that enables serialization and deserialization of AWS SDK v2 model types.
 *
 * <p>This class keeps the original module name for callers migrating from Jackson 2. Register it on
 * a Jackson 3 {@code ObjectMapper} that needs to handle v2 SDK objects.
 */
public class AwsSdkV2Module extends AwsSdkV2Jackson3Module {

  public AwsSdkV2Module() {
    super();
  }
}
