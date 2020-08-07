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
 *
 */

package com.netflix.spinnaker.config;

import java.util.Map;
import javax.annotation.Nonnull;

/** Endpoint config used to build clients. */
public interface ServiceEndpoint {

  /** Name of the service */
  @Nonnull
  public String getName();

  /** Base API url */
  @Nonnull
  public String getBaseUrl();

  /** Misc. config necessary for the service client. */
  @Nonnull
  public Map<String, Object> getConfig();
}
