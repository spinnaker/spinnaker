/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.config.CloudDriverConfigurationProperties;
import com.netflix.spinnaker.orca.clouddriver.config.LambdaConfigurationProperties;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaCacheRefreshInput;
import com.netflix.spinnaker.orca.clouddriver.utils.LambdaCloudDriverUtils;
import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.Data;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LambdaCacheRefreshTask implements LambdaStageBaseTask {
  private static final Logger logger = LoggerFactory.getLogger(LambdaCacheRefreshTask.class);
  private static final String CLOUDDRIVER_REFRESH_CACHE_PATH = "/cache/aws/function";
  private final OkHttpClient client;

  CloudDriverConfigurationProperties props;

  private LambdaCloudDriverUtils utils;

  LambdaConfigurationProperties config;

  @Autowired
  public LambdaCacheRefreshTask(
      CloudDriverConfigurationProperties props,
      LambdaCloudDriverUtils utils,
      OkHttpClient client,
      LambdaConfigurationProperties config) {
    this.props = props;
    this.utils = utils;
    this.client = client;
    this.config = config;
  }

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    logger.debug("Executing LambdaCacheRefreshTask...");
    prepareTask(stage);
    try {
      forceCacheRefresh(stage, 15);
      return taskComplete(stage);
    } catch (Exception e) {
      return TaskResult.builder(ExecutionStatus.TERMINAL)
          .context(getTaskContext(stage))
          .outputs(stage.getOutputs())
          .build();
    }
  }

  @Data
  public static class OnDemandResponse {
    Map<String, List<String>> cachedIdentifiersByType;
  }

  /*
  TWO try loops in here, ONE is embedded in the other.
  - Retry until a force cache operation actually goes through
  - AFTER that, retry until cache refresh finishes
   */
  private void forceCacheRefresh(StageExecution stage, int tries) {
    LambdaCacheRefreshInput inp = utils.getInput(stage, LambdaCacheRefreshInput.class);
    inp.setAppName(stage.getExecution().getApplication());
    inp.setCredentials(inp.getAccount());

    final RequestBody body =
        RequestBody.create(MediaType.parse("application/json"), utils.asString(inp));
    final Request request =
        new Request.Builder()
            .url(props.getCloudDriverBaseUrl() + CLOUDDRIVER_REFRESH_CACHE_PATH)
            .headers(utils.buildHeaders())
            .post(body)
            .build();
    long startTime = System.currentTimeMillis();
    new RetrySupport()
        .retry(
            () -> {
              try {
                Call call = client.newCall(request);
                Response response = call.execute();
                String respString = response.body().string();
                OnDemandResponse onDemandRefreshResponse;
                switch (response.code()) {
                    // 200 == usually a Delete operation... aka an eviction.
                  case 200:
                    logger.debug("cache refresh responded with 200");
                    return true;
                    // Async processing... meaning we have to poll to see when the refresh
                    // finished....
                  case 202:
                    logger.debug("cache refresh responded with 202");
                    onDemandRefreshResponse =
                        objectMapper.readValue(respString, OnDemandResponse.class);
                    if (StringUtils.isEmpty(
                        onDemandRefreshResponse
                            .getCachedIdentifiersByType()
                            .get("onDemand")
                            .isEmpty())) {
                      throw new NotFoundException(
                          "Force cache refresh did not return the id of the cache to run.  We failed or cache refresh isn't working as expected");
                    }
                    String id =
                        onDemandRefreshResponse.getCachedIdentifiersByType().get("onDemand").get(0);
                    waitForCacheToComplete(id, startTime, props.getCloudDriverBaseUrl(), 30);
                    return true;
                  default:
                    logger.warn(
                        "Failed to generate a force cache refresh with response code of "
                            + response.code()
                            + "... retrying.");
                    throw new RuntimeException(
                        "Failed to process cache request due to an invalid response code... retrying...");
                }
              } catch (IOException e) {
                logger.error("cache- refresh failed: " + e.getMessage());
                logger.error(e.getStackTrace().toString());
                throw new RuntimeException("Error communicating with clouddriver", e);
              }
            },
            tries,
            Duration.ofSeconds(config.getCacheRefreshRetryWaitTime()),
            false);
  }

  /*
     IF no data, trigger a force refresh
     if processedCount > 0 && processedTime > stageStart
         SUCCESS
     ELSE
         TRIGGER A REFRESH
  */
  private void waitForCacheToComplete(
      String id, long taskStartTime, String cloudDriverUrl, int retries) {

    new RetrySupport()
        .retry(
            () -> {
              try {
                String response =
                    utils.getFromCloudDriver(cloudDriverUrl + "/cache/aws/function?id=" + id);
                Collection<Map<String, Object>> onDemands =
                    objectMapper.readValue(response, Collection.class);
                for (Map<String, Object> results : onDemands) {
                  if ((int) results.getOrDefault("processedCount", 0) > 0
                      && (long) results.getOrDefault("cacheTime", 0) > taskStartTime) {
                    logger.info("Caching should be completed for " + id);
                    return true;
                  } else {
                    // This short circuits the loop and triggers a retry after 15 seconds, to see if
                    // processedCount is finished yet...
                    throw new RuntimeException("No cache data has been processed... retrying...");
                  }
                }
                logger.warn("No on demand cache refresh found for  " + id);
                throw new RuntimeException("No on demand cache refresh found for " + id);
              } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
              }
            },
            retries,
            Duration.ofSeconds(config.getCacheOnDemandRetryWaitTime()),
            false);
  }
}
