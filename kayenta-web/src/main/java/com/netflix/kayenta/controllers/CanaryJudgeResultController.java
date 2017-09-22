/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.kayenta.controllers;

import com.netflix.kayenta.canary.results.CanaryJudgeResult;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.CredentialsHelper;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.kayenta.storage.StorageServiceRepository;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/canaryJudgeResult")
@Slf4j
public class CanaryJudgeResultController {

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository;

  @Autowired
  StorageServiceRepository storageServiceRepository;

  @ApiOperation(value = "Retrieve a canary judge result from object storage")
  @RequestMapping(value = "/{canaryJudgeResultId:.+}", method = RequestMethod.GET)
  public CanaryJudgeResult loadCanaryJudgeResult(@RequestParam(required = false) final String accountName,
                                                 @PathVariable String canaryJudgeResultId) {
    String resolvedAccountName = CredentialsHelper.resolveAccountByNameOrType(accountName,
                                                                              AccountCredentials.Type.OBJECT_STORE,
                                                                              accountCredentialsRepository);
    StorageService storageService =
      storageServiceRepository
        .getOne(resolvedAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No storage service was configured; unable to read canary judge result from bucket."));

    return storageService.loadObject(resolvedAccountName, ObjectType.CANARY_JUDGE_RESULT, canaryJudgeResultId);
  }

  @ApiOperation(value = "Write a canary judge result to object storage")
  @RequestMapping(consumes = "application/json", method = RequestMethod.POST)
  public String storeCanaryJudgeResult(@RequestParam(required = false) final String accountName,
                                       @RequestBody CanaryJudgeResult canaryJudgeResult) throws IOException {
    String resolvedAccountName = CredentialsHelper.resolveAccountByNameOrType(accountName,
                                                                              AccountCredentials.Type.OBJECT_STORE,
                                                                              accountCredentialsRepository);
    StorageService storageService =
      storageServiceRepository
        .getOne(resolvedAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No storage service was configured; unable to write canary judge result to bucket."));
    String canaryJudgeResultId = UUID.randomUUID() + "";

    storageService.storeObject(resolvedAccountName, ObjectType.CANARY_JUDGE_RESULT, canaryJudgeResultId, canaryJudgeResult);

    return canaryJudgeResultId;
  }

  @ApiOperation(value = "Delete a canary judge result")
  @RequestMapping(value = "/{canaryJudgeResultId:.+}", method = RequestMethod.DELETE)
  public void deleteCanaryJudgeResult(@RequestParam(required = false) final String accountName,
                                      @PathVariable String canaryJudgeResultId,
                                      HttpServletResponse response) {
    String resolvedAccountName = CredentialsHelper.resolveAccountByNameOrType(accountName,
                                                                              AccountCredentials.Type.OBJECT_STORE,
                                                                              accountCredentialsRepository);
    StorageService storageService =
      storageServiceRepository
        .getOne(resolvedAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No storage service was configured; unable to delete canary judge result."));

    storageService.deleteObject(resolvedAccountName, ObjectType.CANARY_JUDGE_RESULT, canaryJudgeResultId);

    response.setStatus(HttpStatus.NO_CONTENT.value());
  }

  @ApiOperation(value = "Retrieve a list of canary judge result ids and timestamps")
  @RequestMapping(method = RequestMethod.GET)
  public List<Map<String, Object>> listAllCanaryJudgeResults(@RequestParam(required = false) final String accountName) {
    String resolvedAccountName = CredentialsHelper.resolveAccountByNameOrType(accountName,
                                                                              AccountCredentials.Type.OBJECT_STORE,
                                                                              accountCredentialsRepository);
    StorageService storageService =
      storageServiceRepository
        .getOne(resolvedAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No storage service was configured; unable to list all canary judge results."));

      return storageService.listObjectKeys(resolvedAccountName, ObjectType.CANARY_JUDGE_RESULT);
  }
}
