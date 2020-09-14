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

package com.netflix.spinnaker.credentials.poller;

import com.netflix.spinnaker.credentials.Credentials;
import com.netflix.spinnaker.credentials.definition.AbstractCredentialsLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A poller attempts to reload credentials from its source at a regular interval.
 *
 * <p>Pollers are enabled by `credentials.poller.enabled`. Frequency is given by the property
 * `credentials.poller.[credentials type].reloadFrequencyMs` and defaults to
 * `credentials.poller.default.reloadFrequencyMs`. If not provided, the default is zero which
 * disables the polling.
 *
 * @param <T>
 */
@Slf4j
@RequiredArgsConstructor
public class Poller<T extends Credentials> implements Runnable {
  private final AbstractCredentialsLoader<T> credentialsLoader;

  public void run() {
    try {
      credentialsLoader.load();
    } catch (Exception e) {
      log.error("Error reloading repository", e);
    }
  }
}
