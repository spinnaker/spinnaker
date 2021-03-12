/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.orca.clouddriver.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Data;

/** A step towards a more standard cloud operation context object. */
@Data
@NonnullByDefault
public class OperationContext {

  private final Map<String, Object> backing = new HashMap<>();

  /** The name AtomicOperation type. */
  private String operationType;

  /** The cloud provider name. */
  private String cloudProvider;

  /** The credentials name (clouddriver account name). */
  private String credentials;

  /**
   * The operation lifecycle.
   *
   * <p>This is an internal-only field and should not be set by users. If this property is set by an
   * end-user, it will be silently scrubbed.
   */
  @Nullable private OperationLifecycle operationLifecycle;

  @JsonAnyGetter
  public Map<String, Object> getBacking() {
    return backing;
  }

  @JsonAnySetter
  public void setBackingValue(String key, @Nullable Object value) {
    backing.put(key, value);
  }
}
