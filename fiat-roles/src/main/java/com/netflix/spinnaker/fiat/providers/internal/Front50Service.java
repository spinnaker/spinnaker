/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.fiat.providers.internal;

import com.netflix.spinnaker.fiat.model.resources.Application;
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount;
import java.util.List;

public class Front50Service {

  private final Front50ApplicationLoader front50ApplicationLoader;
  private final Front50ServiceAccountLoader front50ServiceAccountLoader;

  public Front50Service(
      Front50ApplicationLoader front50ApplicationLoader,
      Front50ServiceAccountLoader front50ServiceAccountLoader) {
    this.front50ApplicationLoader = front50ApplicationLoader;
    this.front50ServiceAccountLoader = front50ServiceAccountLoader;
  }

  /** @deprecated use {@code getAllApplications} - the restricted parameter is ignored */
  @Deprecated
  public List<Application> getAllApplications(boolean restricted) {
    return getAllApplications();
  }

  public List<Application> getAllApplications() {
    return front50ApplicationLoader.getData();
  }

  public List<ServiceAccount> getAllServiceAccounts() {
    return front50ServiceAccountLoader.getData();
  }
}
