/*
 * Copyright 2023 The original authors.
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

package com.netflix.spinnaker.clouddriver.controllers.resources

import com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository
import com.netflix.spinnaker.credentials.definition.CredentialsDefinition
import groovy.util.logging.Slf4j

import javax.annotation.Nullable
import java.util.concurrent.ConcurrentHashMap

/**
 * An in-memory repository of {@link CredentialsDefinition} objects.
 */
@Slf4j
public class MapBackedAccountDefinitionRepository implements AccountDefinitionRepository {
    private final Map<String, ? extends CredentialsDefinition> map = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(String name) {
      map.remove(name)
    }

    /**
       * {@inheritDoc}
       */
    @Override
    CredentialsDefinition getByName(String name) {
      return map.get(name)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    List<? extends CredentialsDefinition> listByType(String typeName, int limit, @Nullable String startingAccountName) {
      def list = listByType(typeName)

      if (startingAccountName != null) {
        list = list.findAll { acct -> acct.name.startsWith(startingAccountName) }
      }

      if (limit < list.size()) {
        list = list.subList(0, limit)
      }

      return list
    }

    /**
     * {@inheritDoc}
     */
    @Override
    List<? extends CredentialsDefinition> listByType(String typeName) {
      return new ArrayList<>(map.findAll{_, definition -> definition.accountType == typeName}.values())
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void create(CredentialsDefinition definition) {
      map.put(definition.getName(), definition)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void save(CredentialsDefinition definition) {
      map.put(definition.getName(), definition)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    void update(CredentialsDefinition definition) {
      map.put(definition.getName(), definition)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    List<Revision> revisionHistory(String name) {
      return List.of()
    }
}
