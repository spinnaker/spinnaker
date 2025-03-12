/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.igor.polling;

import java.util.HashMap;
import java.util.Map;

public class PollContext {
  public final String partitionName;
  public final Map<String, Object> context;
  public final boolean fastForward;

  public PollContext(String partitionName) {
    this(partitionName, new HashMap<>());
  }

  public PollContext(String partitionName, Map<String, Object> context) {
    this(partitionName, context, false);
  }

  public PollContext(String partitionName, boolean fastForward) {
    this(partitionName, new HashMap<>(), fastForward);
  }

  public PollContext(String partitionName, Map<String, Object> context, boolean fastForward) {
    this.partitionName = partitionName;
    this.context = context;
    this.fastForward = fastForward;
  }

  public PollContext fastForward() {
    return new PollContext(partitionName, context, true);
  }
}
