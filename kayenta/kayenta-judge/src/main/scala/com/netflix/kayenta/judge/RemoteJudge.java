/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.kayenta.judge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.canary.CanaryClassifierThresholdsConfig;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryJudge;
import com.netflix.kayenta.canary.results.CanaryJudgeResult;
import com.netflix.kayenta.judge.config.RemoteJudgeConfigurationProperties;
import com.netflix.kayenta.judge.model.RemoteJudgeRequest;
import com.netflix.kayenta.judge.service.RemoteJudgeService;
import com.netflix.kayenta.metrics.MetricSetPair;
import com.netflix.kayenta.retrofit.config.RemoteService;
import com.netflix.kayenta.retrofit.config.RetrofitClientFactory;
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Component
@ConditionalOnProperty("kayenta.remote-judge.enabled")
@Slf4j
public class RemoteJudge extends CanaryJudge {

  private final RetrofitClientFactory retrofitClientFactory;
  private final ObjectMapper kayentaObjectMapper;
  private final RemoteService endpoint;
  private final OkHttp3ClientConfiguration okHttp3ClientConfig;

  private final String JUDGE_NAME = "RemoteJudge-v1.0";

  public RemoteJudge(
      RetrofitClientFactory retrofitClientFactory,
      ObjectMapper kayentaObjectMapper,
      RemoteJudgeConfigurationProperties config,
      OkHttp3ClientConfiguration okHttp3ClientConfig) {
    this.retrofitClientFactory = retrofitClientFactory;
    this.kayentaObjectMapper = kayentaObjectMapper;
    this.endpoint = config.getEndpoint();
    this.okHttp3ClientConfig = okHttp3ClientConfig;

    log.info("Configured " + JUDGE_NAME + " with base URI " + endpoint.getBaseUrl());
  }

  @Override
  public String getName() {
    return JUDGE_NAME;
  }

  @Override
  public boolean isVisible() {
    return true;
  }

  @Override
  public CanaryJudgeResult judge(
      CanaryConfig canaryConfig,
      CanaryClassifierThresholdsConfig scoreThresholds,
      List<MetricSetPair> metricSetPairList) {

    OkHttpClient okHttpClient =
        okHttp3ClientConfig
            .createForRetrofit2()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build();

    RemoteJudgeService remoteJudge =
        retrofitClientFactory.createClient(
            RemoteJudgeService.class,
            JacksonConverterFactory.create(kayentaObjectMapper),
            endpoint,
            okHttpClient);

    RemoteJudgeRequest judgeRequest =
        RemoteJudgeRequest.builder()
            .canaryConfig(canaryConfig)
            .metricSetPairList(metricSetPairList)
            .scoreThresholds(scoreThresholds)
            .build();

    return remoteJudge.judge(judgeRequest);
  }
}
