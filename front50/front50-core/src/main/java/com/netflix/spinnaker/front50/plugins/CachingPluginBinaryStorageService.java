/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.front50.plugins;

import com.netflix.spinnaker.kork.exceptions.SystemException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CachingPluginBinaryStorageService implements PluginBinaryStorageService {

  private static final Path CACHE_PATH;

  private static final Logger log =
      LoggerFactory.getLogger(CachingPluginBinaryStorageService.class);

  static {
    try {
      CACHE_PATH = Files.createTempDirectory("plugin-binaries");
    } catch (IOException e) {
      throw new SystemException("Failed to create plugin binaries cache directory", e);
    }
  }

  private final PluginBinaryStorageService storageService;

  public CachingPluginBinaryStorageService(PluginBinaryStorageService storageService) {
    this.storageService = storageService;
  }

  @Override
  public void store(@Nonnull String key, @Nonnull byte[] item) {
    storageService.store(key, item);
    storeCache(key, item);
  }

  @Override
  public void delete(@Nonnull String key) {
    storageService.delete(key);
    deleteCache(key);
  }

  @Nonnull
  @Override
  public List<String> listKeys() {
    return storageService.listKeys();
  }

  @Nullable
  @Override
  public byte[] load(@Nonnull String key) {
    return loadFromCache(key);
  }

  private byte[] loadFromCache(String key) {
    Path binaryPath = CACHE_PATH.resolve("key");
    if (binaryPath.toFile().exists()) {
      try {
        return Files.readAllBytes(binaryPath);
      } catch (IOException e) {
        log.error("Failed to read cached binary, falling back to delegate store: {}", key, e);
      }
    }
    return loadInternal(key);
  }

  private byte[] loadInternal(String key) {
    byte[] binary = storageService.load(key);
    if (binary == null) {
      return null;
    }

    storeCache(key, binary);

    return binary;
  }

  private synchronized void storeCache(String key, byte[] binary) {
    Path binaryPath = CACHE_PATH.resolve(key);
    if (!binaryPath.toFile().exists()) {
      try {
        Files.createDirectories(binaryPath.getParent());
        Files.write(binaryPath, binary, StandardOpenOption.CREATE_NEW);
      } catch (IOException e) {
        log.error("Failed to write plugin binary to local filesystem cache: {}", key, e);
      }
    }
  }

  private synchronized void deleteCache(String key) {
    try {
      Files.deleteIfExists(CACHE_PATH.resolve(key));
    } catch (IOException e) {
      log.error("Failed to delete plugin binary from local filesystem cache: {}", key, e);
    }
  }
}
