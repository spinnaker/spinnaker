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
package com.netflix.spinnaker.front50.controllers;

import static java.lang.String.format;

import com.google.common.hash.Hashing;
import com.netflix.spinnaker.front50.plugins.PluginBinaryStorageService;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pluginBinaries")
public class PluginBinaryController {

  private final PluginBinaryStorageService pluginBinaryStorageService;

  public PluginBinaryController(PluginBinaryStorageService pluginBinaryStorageService) {
    this.pluginBinaryStorageService = pluginBinaryStorageService;
  }

  @PostMapping("/{id}/{version}")
  @ResponseStatus(HttpStatus.CREATED)
  void upload(
      @PathVariable String id,
      @PathVariable String version,
      @RequestParam("sha512sum") String sha512sum,
      @RequestBody byte[] body) {
    verifyChecksum(body, sha512sum);
    pluginBinaryStorageService.store(getKey(id, version), body);
  }

  @GetMapping("/{id}/{version}")
  ResponseEntity<byte[]> getBinary(@PathVariable String id, @PathVariable String version) {
    return ResponseEntity.ok()
        .header("Content-Type", "application/octet-stream")
        .body(pluginBinaryStorageService.load(getKey(id, version)));
  }

  private static String getKey(String id, String version) {
    return format("%s/%s.zip", id, version);
  }

  private void verifyChecksum(byte[] body, String sha512sum) {
    String sha = Hashing.sha512().hashBytes(body).toString();
    if (!sha.equals(sha512sum)) {
      throw new SystemException("Plugin binary checksum does not match expected checksum value")
          .setRetryable(true);
    }
  }
}
