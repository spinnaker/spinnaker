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

package com.netflix.spinnaker.amos;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Implementations of this interface will provide properties specific to a named account object,
 * with capability to retrieve a type of credential object (such as AWSCredentials or GoogleCredentials).
 *
 * @author Dan Woods
 * @param <T> - type of credential object to be returned
 */
public interface AccountCredentials<T> {
    /**
     * Provides the name of the account to be returned. May be an environment name, an account name, etc.
     *
     * @return the name of the account
     */
    String getName();

    /**
     * Returns an associated credentials object, which may be lazily initialized based off of some detail encapsulated
     * within the implementation (like environment or keys, etc)
     *
     * @return typed credentials object
     */
    @JsonIgnore T getCredentials();

    /**
     * Provides the name of the cloud provider. Typically something like 'aws', 'gce' or 'docker'.
     *
     * @return the name of the cloud provider
     */
    String getProvider();
}
