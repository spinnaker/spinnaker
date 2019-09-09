/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.kork.test.mimicker.producers;

import com.netflix.spinnaker.moniker.Moniker;
import java.util.StringJoiner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** TODO(rz): Only supports Frigga formatted monikers at the moment. */
public class MonikerProducer {

  private static final int BOUND_MAX = 32;

  @NotNull private final RandomProducer randomProducer;

  public MonikerProducer(@NotNull RandomProducer randomProducer) {
    this.randomProducer = randomProducer;
  }

  @Nullable
  public Moniker get() {
    Moniker.MonikerBuilder builder = Moniker.builder().sequence(randomProducer.intValue(0, 999));

    String app = randomProducer.alphanumeric(1, BOUND_MAX);
    String stack = randomProducer.trueOrFalse() ? randomProducer.alphanumeric(0, BOUND_MAX) : null;
    String detail = randomProducer.trueOrFalse() ? randomProducer.alphanumeric(0, BOUND_MAX) : null;

    builder.app(app);

    StringJoiner clusterJoiner = new StringJoiner("-").add(app);
    if (stack != null) {
      builder.stack(stack);
      clusterJoiner.add(stack);
    }
    if (detail != null) {
      builder.detail(detail);
      clusterJoiner.add(detail);
    }

    return builder.cluster(clusterJoiner.toString()).build();
  }
}
