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

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.TimeWindowFixedBoundaryHistogram;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.core.lang.Nullable;
import io.prometheus.client.exemplars.Exemplar;
import io.prometheus.client.exemplars.HistogramExemplarSampler;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class MutatedPrometheusHistogram extends TimeWindowFixedBoundaryHistogram {
  private final double[] buckets;
  private final AtomicReferenceArray<Exemplar> exemplars;
  @Nullable private final HistogramExemplarSampler exemplarSampler;

  MutatedPrometheusHistogram(
      Clock clock,
      DistributionStatisticConfig config,
      @Nullable HistogramExemplarSampler exemplarSampler) {
    super(
        clock,
        DistributionStatisticConfig.builder()
            .expiry(Duration.ofDays(1825L))
            .bufferLength(1)
            .build()
            .merge(config),
        true);
    this.exemplarSampler = exemplarSampler;
    if (this.isExemplarsEnabled()) {
      double[] originalBuckets = this.getBuckets();
      if (originalBuckets[originalBuckets.length - 1] != Double.POSITIVE_INFINITY) {
        this.buckets = Arrays.copyOf(originalBuckets, originalBuckets.length + 1);
        this.buckets[this.buckets.length - 1] = Double.POSITIVE_INFINITY;
      } else {
        this.buckets = originalBuckets;
      }

      this.exemplars = new AtomicReferenceArray(this.buckets.length);
    } else {
      this.buckets = null;
      this.exemplars = null;
    }
  }

  boolean isExemplarsEnabled() {
    return this.exemplarSampler != null;
  }

  public void recordDouble(double value) {
    super.recordDouble(value);
    if (this.isExemplarsEnabled()) {
      this.updateExemplar(value, (TimeUnit) null, (TimeUnit) null);
    }
  }

  public void recordLong(long value) {
    super.recordLong(value);
    if (this.isExemplarsEnabled()) {
      this.updateExemplar((double) value, TimeUnit.NANOSECONDS, TimeUnit.SECONDS);
    }
  }

  private void updateExemplar(
      double value, @Nullable TimeUnit sourceUnit, @Nullable TimeUnit destinationUnit) {
    int index = this.leastLessThanOrEqualTo(value);
    index = index == -1 ? this.exemplars.length() - 1 : index;
    this.updateExemplar(value, sourceUnit, destinationUnit, index);
  }

  private void updateExemplar(
      double value, @Nullable TimeUnit sourceUnit, @Nullable TimeUnit destinationUnit, int index) {
    double bucketFrom = index == 0 ? Double.NEGATIVE_INFINITY : this.buckets[index - 1];
    double bucketTo = this.buckets[index];
    double exemplarValue =
        sourceUnit != null && destinationUnit != null
            ? TimeUtils.convert(value, sourceUnit, destinationUnit)
            : value;

    Exemplar prev;
    Exemplar next;
    do {
      prev = (Exemplar) this.exemplars.get(index);
      next = this.exemplarSampler.sample(exemplarValue, bucketFrom, bucketTo, prev);
    } while (next != null && next != prev && !this.exemplars.compareAndSet(index, prev, next));
  }

  @Nullable
  Exemplar[] exemplars() {
    if (!this.isExemplarsEnabled()) {
      return null;
    } else {
      Exemplar[] exemplarsArray = new Exemplar[this.exemplars.length()];

      for (int i = 0; i < this.exemplars.length(); ++i) {
        exemplarsArray[i] = (Exemplar) this.exemplars.get(i);
      }

      return exemplarsArray;
    }
  }

  private int leastLessThanOrEqualTo(double key) {
    int low = 0;
    int high = this.buckets.length - 1;

    while (low <= high) {
      int mid = low + high >>> 1;
      if (this.buckets[mid] < key) {
        low = mid + 1;
      } else {
        if (!(this.buckets[mid] > key)) {
          return mid;
        }

        high = mid - 1;
      }
    }

    return low < this.buckets.length ? low : -1;
  }
}
