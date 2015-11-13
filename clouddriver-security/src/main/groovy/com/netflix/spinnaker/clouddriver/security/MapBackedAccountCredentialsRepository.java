/*
 * Copyright 2015 Netflix, Inc.
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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An in-memory repository of {@link AccountCredentials} objects.
 *
 *
 */
public class MapBackedAccountCredentialsRepository implements AccountCredentialsRepository {
    private final Map<String, AccountCredentials> map = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public AccountCredentials getOne(String key) {
        return map.get(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<AccountCredentials> getAll() {
        return new HashSet<>(map.values());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AccountCredentials save(String key, AccountCredentials credentials) {
        return map.put(credentials.getName(), credentials);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AccountCredentials update(String key, AccountCredentials credentials) {
        return save(key, credentials);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(String name) {
        map.remove(name);
    }
}
