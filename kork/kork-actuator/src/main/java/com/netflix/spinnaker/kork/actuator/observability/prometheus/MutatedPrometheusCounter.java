/*
 * Copyright 2025 Netflix, Inc.
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

package com.netflix.spinnaker.kork.actuator.observability.prometheus;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.lang.NonNull;
import io.micrometer.core.lang.Nullable;
import io.prometheus.client.exemplars.CounterExemplarSampler;
import io.prometheus.client.exemplars.Exemplar;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.DoubleAdder;

public class MutatedPrometheusCounter extends AbstractMeter implements Counter {
  private final DoubleAdder count;
  private final AtomicReference<Exemplar> exemplar;
  @Nullable private final CounterExemplarSampler exemplarSampler;

  MutatedPrometheusCounter(Id id) {
    this(id, (CounterExemplarSampler) null);
  }

  MutatedPrometheusCounter(Id id, @Nullable CounterExemplarSampler exemplarSampler) {
    super(id);
    this.count = new DoubleAdder();
    this.exemplar = new AtomicReference();
    this.exemplarSampler = exemplarSampler;
  }

  public void increment(double amount) {
    if (amount > 0.0) {
      this.count.add(amount);
      if (this.exemplarSampler != null) {
        this.updateExemplar(amount, this.exemplarSampler);
      }
    }
  }

  public double count() {
    return this.count.doubleValue();
  }

  @Nullable
  Exemplar exemplar() {
    return (Exemplar) this.exemplar.get();
  }

  private void updateExemplar(double amount, @NonNull CounterExemplarSampler exemplarSampler) {
    Exemplar prev;
    Exemplar next;
    do {
      prev = (Exemplar) this.exemplar.get();
      next = exemplarSampler.sample(amount, prev);
    } while (next != null && next != prev && !this.exemplar.compareAndSet(prev, next));
  }
}
