/*
 * Copyright 2020 Armory
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

package com.netflix.spinnaker.credentials.definition;

import com.netflix.spinnaker.credentials.Credentials;
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint;
import javax.annotation.Nullable;

/**
 * Instantiate {@link Credentials} from a {@link CredentialsDefinition}.
 *
 * @param <T>
 * @param <U>
 */
public interface CredentialsParser<T extends CredentialsDefinition, U extends Credentials>
    extends SpinnakerExtensionPoint {
  /** Parses a definition into credentials. Can return null if the definition is to be ignored. */
  @Nullable
  U parse(T credentials);
}
