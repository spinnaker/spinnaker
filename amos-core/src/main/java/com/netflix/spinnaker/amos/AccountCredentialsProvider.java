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

import java.util.Set;

/**
 * Implementations of this interface will provide a mechanism to store and retrieve {@link com.netflix.spinnaker.amos.AccountCredentials}
 * objects.
 *
 * @author Dan Woods
 */
public interface AccountCredentialsProvider {

    /**
     * Returns the names of all of the accounts known to the repository of this provider.
     *
     * @return a set of account names
     */
    Set<String> getAccountNames();

    /**
     * Returns a specific {@link com.netflix.spinnaker.amos.AccountCredentials} object a specified name
     *
     * @param name
     * @return account credentials object
     */
    AccountCredentials getCredentials(String name);

    /**
     * Stores an {@link com.netflix.spinnaker.amos.AccountCredentials} object in this provider's internal repository, which
     * will be recalled by some calculable name (such as {@link AccountCredentials#getName()}.
     *
     * @param accountCredentials
     */
    void put(AccountCredentials accountCredentials);
}
