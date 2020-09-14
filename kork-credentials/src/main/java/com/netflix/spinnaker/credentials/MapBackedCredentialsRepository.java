/*
 * Copyright 2020 Armory
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
 */

package com.netflix.spinnaker.credentials;

import com.netflix.spinnaker.kork.exceptions.InvalidCredentialsTypeException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import lombok.Getter;

public class MapBackedCredentialsRepository<T extends Credentials>
    implements CredentialsRepository<T> {
  protected Map<String, T> credentials = new ConcurrentHashMap<>();
  @Nullable protected CredentialsLifecycleHandler<T> eventHandler;
  @Getter protected String type;

  public MapBackedCredentialsRepository(
      String type, @Nullable CredentialsLifecycleHandler<T> eventHandler) {
    this.type = type;
    this.eventHandler = eventHandler;
  }

  @Override
  public T getOne(String key) {
    return credentials.get(key);
  }

  @Override
  public boolean has(String name) {
    return credentials.containsKey(name);
  }

  @Override
  public Set<T> getAll() {
    return new HashSet<>(credentials.values());
  }

  @Override
  public void save(T creds) {
    if (!creds.getType().equals(getType())) {
      throw new InvalidCredentialsTypeException(
          "Credentials '"
              + creds.getName()
              + "' of type '"
              + creds.getType()
              + "' cannot be added to repository of type '"
              + getType()
              + "'");
    }
    if (eventHandler != null) {
      if (credentials.containsKey(creds.getName())) {
        eventHandler.credentialsUpdated(creds);
      } else {
        eventHandler.credentialsAdded(creds);
      }
    }
    credentials.put(creds.getName(), creds);
  }

  @Override
  public void delete(String key) {
    T credentials = this.credentials.remove(key);
    if (credentials != null && eventHandler != null) {
      eventHandler.credentialsDeleted(credentials);
    }
  }
}
