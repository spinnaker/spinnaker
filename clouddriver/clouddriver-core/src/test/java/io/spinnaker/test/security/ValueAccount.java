/*
 * Copyright 2022 Apple Inc.
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

package io.spinnaker.test.security;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinition;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

// TODO(jvz): change to @CredentialsType after https://github.com/spinnaker/kork/pull/958 merged
@JsonTypeName("value")
@NonnullByDefault
@Value
@Builder
@Jacksonized
public class ValueAccount implements CredentialsDefinition {
  String name;
  String value;
}
