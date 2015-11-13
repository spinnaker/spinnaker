/*
 * Copyright 2014-2015 Netflix, Inc.
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

package com.netflix.spinnaker.oort.model

import com.netflix.spinnaker.oort.documentation.Empty
import com.netflix.spinnaker.oort.documentation.Nullable

/**
 * An application provider is an interface for which {@link Application} objects may be retrieved. This interface defines a common contract for which various providers may be queried about their
 * known applications.
 *
 *
 *
 */
interface ApplicationProvider {
  /**
   * Looks up all of the {@link Application} objects known to this provider
   *
   * @return a set of applications or an empty set if none are known to this provider
   */
  @Empty
  Set<? extends Application> getApplications()

  /**
   * Looks up a particular application by name
   *
   * @param application name
   * @return an application or null if it is not known to this provider
   */
  @Nullable
  Application getApplication(String name)
}
