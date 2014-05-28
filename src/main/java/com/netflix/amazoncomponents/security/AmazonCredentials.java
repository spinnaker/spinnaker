/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.amazoncomponents.security;

import com.amazonaws.auth.AWSCredentials;

/**
 * Provides a correlation between an AWSCredentials object and a named environment.
 */
public class AmazonCredentials {
  private final AWSCredentials credentials;
  private final String environment;

  public AmazonCredentials(AWSCredentials credentials, String environment) {
    this.credentials = credentials;
    this.environment = environment;
  }

  public AWSCredentials getCredentials() {
    return credentials;
  }

  public String getEnvironment() {
    return environment;
  }
}
