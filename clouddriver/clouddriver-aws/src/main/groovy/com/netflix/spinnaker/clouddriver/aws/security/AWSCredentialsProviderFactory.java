/*
 * Copyright 2021 Salesforce, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.security;

import com.amazonaws.auth.AWSCredentialsProvider;

public interface AWSCredentialsProviderFactory {
  /**
   * Create an AWSCredentialsProvider object
   *
   * @param profileName the profile name requesting an AWSCredentialsProvider
   * @return an AWSAccountInfoLookup object
   */
  AWSCredentialsProvider makeAWSCredentialsProvider(String profileName);
}
