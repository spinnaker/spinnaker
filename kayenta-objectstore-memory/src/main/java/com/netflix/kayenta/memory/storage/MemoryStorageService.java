/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.kayenta.memory.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.memory.security.MemoryNamedAccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Builder
class ObjectMetadata {
    @NotNull
    public final String name;
}

@Builder
@Slf4j
public class MemoryStorageService implements StorageService {

    public static class MemoryStorageServiceBuilder {
        private Map<String, String> entries = new ConcurrentHashMap<String, String>();
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @NotNull
    @Singular
    @Getter
    private List<String> accountNames;

    @Autowired
    AccountCredentialsRepository accountCredentialsRepository;

    private Map<String, String> entries;

    @Override
    public boolean servicesAccount(String accountName) {
        return accountNames.contains(accountName);
    }

    @Override
    public <T> T loadObject(String accountName, ObjectType objectType, String objectKey) throws IllegalArgumentException {
        String key = makekey(accountName, objectType, objectKey);
        log.info("Getting key {}", key);
        String json = entries.get(key);
        if (json == null)
            throw new IllegalArgumentException("No such object named " + key);

        try {
            return objectMapper.readValue(json, objectType.getTypeReference());
        } catch (IOException e) {
            log.error("Read failed on path {}: {}", key, e);
            throw new IllegalStateException(e);
        }
    }

    @Override
    public <T> void storeObject(String accountName, ObjectType objectType, String objectKey, T obj) {
        String key = makekey(accountName, objectType, objectKey);
        log.info("Writing key {}", key);
        try {
            String json = objectMapper.writeValueAsString(obj);
            entries.put(key, json);
        } catch (IOException e) {
            log.error("Update failed on path {}: {}", key, e);
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void deleteObject(String accountName, ObjectType objectType, String objectKey) {
        String key = makekey(accountName, objectType, objectKey);
        log.info("Deleting key {}", key);
        String oldValue = entries.remove(key);
        if (oldValue == null) {
            log.error("Object named {} does not exist", key);
            throw new IllegalStateException("Does not exist");
        }
    }

    @Override
    public List<Map<String, Object>> listObjectKeys(String accountName, ObjectType objectType) {
        String prefix = makeprefix(accountName, objectType);

        return entries
                .entrySet()
                .parallelStream()
                .filter(e -> e.getKey().startsWith(prefix))
                .map(e -> mapFrom(e.getKey()))
                .collect(Collectors.toList());
    }

    private Map<String, Object> mapFrom(String key) {
        Map<String, Object> ret = new HashMap<String, Object>();
        ret.put(nameFromKey(key), metadataFor(key));
        return ret;
    }

    private String nameFromKey(String key) {
        return key.split(":", 3)[2];
    }

    private ObjectMetadata metadataFor(String key) {
        return ObjectMetadata.builder().name(nameFromKey(key)).build();
    }

    private String makeprefix(String accountName, ObjectType objectType) {
        MemoryNamedAccountCredentials credentials = (MemoryNamedAccountCredentials)accountCredentialsRepository
                .getOne(accountName)
                .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + accountName + "."));
        String namespace = credentials.getNamespace();
        String typename = objectType.getTypeReference().getType().getTypeName();
        return namespace + ":" + typename + ":";
    }

    private String makekey(String accountName, ObjectType objectType, String objectKey) {
        return makeprefix(accountName, objectType) + objectKey;
    }
}
