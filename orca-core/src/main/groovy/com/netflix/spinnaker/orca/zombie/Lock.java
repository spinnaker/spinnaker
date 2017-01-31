/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.zombie;

import java.util.Optional;
import javax.annotation.Nonnull;
import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Func0;

public interface Lock {
  <N> Optional<Subscription> withLock(
    @Nonnull String uniqueId,
    @Nonnull Func0<Observable<N>> generator,
    @Nonnull Action1<N> onNext);

  Lock NEVER_LOCKED = new Lock() {
    @Override
    public <N> Optional<Subscription> withLock(
      @Nonnull String uniqueId,
      @Nonnull Func0<Observable<N>> generator,
      @Nonnull Action1<N> onNext) {
      return Optional.of(generator.call().subscribe(onNext));
    }
  };

  Lock ALWAYS_LOCKED = new Lock() {
    @Override
    public <N> Optional<Subscription> withLock(
      @Nonnull String uniqueId,
      @Nonnull Func0<Observable<N>> generator,
      @Nonnull Action1<N> onNext) {
      return Optional.empty();
    }
  };
}
