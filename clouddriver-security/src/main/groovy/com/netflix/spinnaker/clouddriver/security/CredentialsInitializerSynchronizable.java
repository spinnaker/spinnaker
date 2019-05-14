/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.security;

/**
 * This interface is used by the credentials refresh controller to identify credentials initializers
 * that should be re-created when the credentials have changed.
 */
public interface CredentialsInitializerSynchronizable {
  /**
   * Get the name of the bean to request from Spring's application context. It is expected that the
   * Accounts and Agents managed by the credentials initializer will be synchronized with the latest
   * configured accounts as a result of requesting this bean.
   */
  String getCredentialsSynchronizationBeanName();
}
