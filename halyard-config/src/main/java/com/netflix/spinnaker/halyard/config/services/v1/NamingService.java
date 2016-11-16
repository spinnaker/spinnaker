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

package com.netflix.spinnaker.halyard.config.services.v1;

/**
 * Generate human-readable, consistent, uniquely identifiable names for the different constructions returned by our
 * services.
 */
public class NamingService {
  static String quotify(String s) {
    return "\"" + s + "\"";
  }

  static String deployment(String deployment) {
    return "deployment " + quotify(deployment);
  }

  static String provider(String deployment, String provider) {
    return deployment(deployment) + " for provider " + quotify(provider);
  }

  static String account(String deployment, String provider, String account) {
    return provider(deployment, provider) + " with account name " + quotify(account);
  }
}
