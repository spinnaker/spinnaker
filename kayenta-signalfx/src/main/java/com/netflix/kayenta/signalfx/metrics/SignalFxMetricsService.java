/*
 * Copyright (c) 2019 Nike, inc.
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
 *
 */

package com.netflix.kayenta.signalfx.metrics;

import static com.signalfx.signalflow.ChannelMessage.Type.DATA_MESSAGE;
import static com.signalfx.signalflow.ChannelMessage.Type.ERROR_MESSAGE;

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.providers.metrics.QueryPair;
import com.netflix.kayenta.canary.providers.metrics.SignalFxCanaryMetricSetQueryConfig;
import com.netflix.kayenta.metrics.MetricSet;
import com.netflix.kayenta.metrics.MetricsService;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.signalfx.canary.SignalFxCanaryScope;
import com.netflix.kayenta.signalfx.config.SignalFxScopeConfiguration;
import com.netflix.kayenta.signalfx.security.SignalFxNamedAccountCredentials;
import com.netflix.kayenta.signalfx.service.ErrorResponse;
import com.netflix.kayenta.signalfx.service.SignalFlowExecutionResult;
import com.netflix.kayenta.signalfx.service.SignalFxRequestError;
import com.netflix.kayenta.signalfx.service.SignalFxSignalFlowRemoteService;
import com.signalfx.signalflow.ChannelMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import retrofit.RetrofitError;

@Builder
@Slf4j
public class SignalFxMetricsService implements MetricsService {

  private static final String SIGNAL_FLOW_ERROR_TEMPLATE =
      "An error occurred while executing the Signal Flow program. "
          + "account: %s "
          + "startEpochMilli: %s "
          + "endEpochMilli: %s "
          + "stepMilli: %s "
          + "maxDelay: %s "
          + "immediate: %s "
          + "program: %s";

  @NotNull @Singular @Getter private List<String> accountNames;

  @Autowired private final AccountCredentialsRepository accountCredentialsRepository;

  @Autowired private final Map<String, SignalFxScopeConfiguration> signalFxScopeConfigurationMap;

  @Autowired private final SignalFxQueryBuilderService queryBuilder;

  @Override
  public String getType() {
    return "signalfx";
  }

  @Override
  public boolean servicesAccount(String accountName) {
    return accountNames.contains(accountName);
  }

  @Override
  public String buildQuery(
      String metricsAccountName,
      CanaryConfig canaryConfig,
      CanaryMetricConfig canaryMetricConfig,
      CanaryScope canaryScope) {

    SignalFxScopeConfiguration scopeConfiguration =
        signalFxScopeConfigurationMap.get(metricsAccountName);
    SignalFxCanaryScope signalFxCanaryScope = (SignalFxCanaryScope) canaryScope;
    SignalFxCanaryMetricSetQueryConfig queryConfig =
        (SignalFxCanaryMetricSetQueryConfig) canaryMetricConfig.getQuery();

    return queryBuilder.buildQuery(
        canaryConfig, queryConfig, canaryScope, scopeConfiguration, signalFxCanaryScope);
  }

  @Override
  public List<MetricSet> queryMetrics(
      String metricsAccountName,
      CanaryConfig canaryConfig,
      CanaryMetricConfig canaryMetricConfig,
      CanaryScope canaryScope) {

    if (!(canaryScope instanceof SignalFxCanaryScope)) {
      throw new IllegalArgumentException(
          "Canary scope not instance of SignalFxCanaryScope: "
              + canaryScope
              + ". One common cause is having multiple METRICS_STORE accounts configured but "
              + "neglecting to explicitly specify which account to use for a given request.");
    }

    SignalFxCanaryScope signalFxCanaryScope = (SignalFxCanaryScope) canaryScope;
    SignalFxCanaryMetricSetQueryConfig queryConfig =
        (SignalFxCanaryMetricSetQueryConfig) canaryMetricConfig.getQuery();
    List<QueryPair> queryPairs =
        Optional.ofNullable(queryConfig.getQueryPairs()).orElse(new LinkedList<>());

    SignalFxNamedAccountCredentials accountCredentials =
        accountCredentialsRepository.getRequiredOne(metricsAccountName);

    String accessToken = accountCredentials.getCredentials().getAccessToken();
    SignalFxSignalFlowRemoteService signalFlowService = accountCredentials.getSignalFlowService();

    final long maxDelay = 0;
    final boolean immediate = true;
    long startEpochMilli = signalFxCanaryScope.getStart().toEpochMilli();
    long endEpochMilli = signalFxCanaryScope.getEnd().toEpochMilli();
    long canaryStepLengthInSeconds = signalFxCanaryScope.getStep();
    // Determine and validate the data resolution to use for the query
    long stepMilli = Duration.ofSeconds(canaryStepLengthInSeconds).toMillis();

    String program = buildQuery(metricsAccountName, canaryConfig, canaryMetricConfig, canaryScope);

    SignalFlowExecutionResult signalFlowExecutionResult;

    try {
      signalFlowExecutionResult =
          signalFlowService.executeSignalFlowProgram(
              accessToken, startEpochMilli, endEpochMilli, stepMilli, maxDelay, immediate, program);
    } catch (RetrofitError e) {
      ErrorResponse errorResponse = (ErrorResponse) e.getBodyAs(ErrorResponse.class);
      throw new SignalFxRequestError(
          errorResponse, program, startEpochMilli, endEpochMilli, stepMilli, metricsAccountName);
    }

    validateResults(
        signalFlowExecutionResult.getChannelMessages(),
        metricsAccountName,
        startEpochMilli,
        endEpochMilli,
        stepMilli,
        maxDelay,
        immediate,
        program);

    LinkedList<ChannelMessage.DataMessage> dataMessages =
        extractDataMessages(signalFlowExecutionResult);

    ChannelMessage.DataMessage firstDataPoint =
        dataMessages.size() > 0 ? dataMessages.getFirst() : null;

    ChannelMessage.DataMessage lastDataPoint =
        dataMessages.size() > 0 ? dataMessages.getLast() : null;

    // Return a Metric set of the reduced and aggregated data
    MetricSet.MetricSetBuilder metricSetBuilder =
        MetricSet.builder()
            .name(canaryMetricConfig.getName())
            .startTimeMillis(startEpochMilli)
            .startTimeIso(Instant.ofEpochMilli(startEpochMilli).toString())
            .endTimeMillis(endEpochMilli)
            .endTimeIso(Instant.ofEpochMilli(endEpochMilli).toString())
            .stepMillis(stepMilli)
            .values(getTimeSeriesDataFromDataMessages(dataMessages))
            .tags(
                queryPairs.stream()
                    .collect(Collectors.toMap(QueryPair::getKey, QueryPair::getValue)))
            .attribute("signal-flow-program", program)
            .attribute("actual-data-point-count", String.valueOf(dataMessages.size()))
            .attribute("requested-start", String.valueOf(startEpochMilli))
            .attribute("requested-end", String.valueOf(endEpochMilli))
            .attribute("requested-step-milli", String.valueOf(stepMilli))
            .attribute("requested-max-delay", String.valueOf(maxDelay))
            .attribute("requested-immediate", String.valueOf(immediate))
            .attribute("requested-account", metricsAccountName);

    Optional.ofNullable(firstDataPoint)
        .ifPresent(
            dp ->
                metricSetBuilder.attribute(
                    "actual-start-ts", String.valueOf(dp.getLogicalTimestampMs())));
    Optional.ofNullable(lastDataPoint)
        .ifPresent(
            dp ->
                metricSetBuilder.attribute(
                    "actual-end-ts", String.valueOf(dp.getLogicalTimestampMs())));

    return Collections.singletonList(metricSetBuilder.build());
  }

  /**
   * Extracts the messages with data points from the Signal Fx response.
   *
   * @param signalFlowExecutionResult The SignalFlow program results.
   * @return An linked list of the data points from the result.
   */
  private LinkedList<ChannelMessage.DataMessage> extractDataMessages(
      SignalFlowExecutionResult signalFlowExecutionResult) {
    return signalFlowExecutionResult
        .getChannelMessages()
        .parallelStream()
        .filter(channelMessage -> channelMessage.getType().equals(DATA_MESSAGE))
        .map((message) -> (ChannelMessage.DataMessage) message)
        .collect(Collectors.toCollection(LinkedList::new));
  }

  /** Parses the channel messages and the first error if present. */
  private void validateResults(
      List<ChannelMessage> channelMessages,
      String account,
      long startEpochMilli,
      long endEpochMilli,
      long stepMilli,
      long maxDelay,
      boolean immediate,
      String program) {
    channelMessages.stream()
        .filter(channelMessage -> channelMessage.getType().equals(ERROR_MESSAGE))
        .findAny()
        .ifPresent(
            error -> {
              // This error message is terrible, and I am not sure how to add more context to it.
              // error.getErrors() returns a List<Object>, and it is unclear what to do with those.
              throw new RuntimeException(
                  String.format(
                      SIGNAL_FLOW_ERROR_TEMPLATE,
                      account,
                      String.valueOf(startEpochMilli),
                      String.valueOf(endEpochMilli),
                      String.valueOf(stepMilli),
                      String.valueOf(maxDelay),
                      String.valueOf(immediate),
                      program));
            });
  }

  /**
   * Parses the data out of the SignalFx Signal Flow messages to build the data Kayenta needs to
   * make judgements.
   *
   * @param dataMessages The list of data messages from the signal flow execution.
   * @return The list of values with missing data filled with NaNs
   */
  protected List<Double> getTimeSeriesDataFromDataMessages(
      List<ChannelMessage.DataMessage> dataMessages) {
    return dataMessages.stream()
        .map(
            message -> {
              Map<String, Number> data = message.getData();
              if (data.size() > 1) {
                throw new IllegalStateException(
                    "There was more than one value for a given timestamp, a "
                        + "SignalFlow stream method that can aggregate should have been applied to the data in "
                        + "the SignalFlow program");
              }
              return data.size() == 1
                  ? data.values().stream().findFirst().get().doubleValue()
                  : Double.NaN;
            })
        .collect(Collectors.toList());
  }
}
