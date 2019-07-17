/*
 * Copyright 2019 Playtika
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

package com.netflix.kayenta.metrics;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.codehaus.groovy.runtime.InvokerHelper.asList;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.LOCKED;

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.kayenta.storage.StorageServiceRepository;
import com.netflix.spectator.api.Registry;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import retrofit.RetrofitError;
import retrofit.client.Response;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class SynchronousQueryProcessorTest {

  private static final String METRICS = "metrics-account";
  private static final String STORAGE = "storage-account";
  private static final int ATTEMPTS = 5;
  @Mock MetricsRetryConfigurationProperties retryConfiguration;

  @Mock MetricsService metricsService;
  @Mock StorageService storageService;

  @Mock MetricsServiceRepository metricsServiceRepository;

  @Mock StorageServiceRepository storageServiceRepository;

  @Mock(answer = RETURNS_DEEP_STUBS)
  Registry registry;

  @InjectMocks SynchronousQueryProcessor processor;

  @Before
  public void setUp() {
    when(metricsServiceRepository.getOne(METRICS)).thenReturn(Optional.of(metricsService));
    when(storageServiceRepository.getOne(STORAGE)).thenReturn(Optional.of(storageService));
    when(retryConfiguration.getAttempts()).thenReturn(ATTEMPTS);
    when(retryConfiguration.getSeries())
        .thenReturn(
            new HashSet<>(
                Arrays.asList(HttpStatus.Series.SERVER_ERROR, HttpStatus.Series.REDIRECTION)));
    when(retryConfiguration.getStatuses()).thenReturn(new HashSet<>(Arrays.asList(LOCKED)));
  }

  @Test
  public void retriesRetryableHttpSeriesTillMaxAttemptsAndThrowsException() throws IOException {
    when(metricsService.queryMetrics(
            anyString(),
            any(CanaryConfig.class),
            any(CanaryMetricConfig.class),
            any(CanaryScope.class)))
        .thenThrow(getRetrofitErrorWithHttpStatus(INTERNAL_SERVER_ERROR.value()));

    assertThatThrownBy(
            () ->
                processor.executeQuery(
                    METRICS,
                    STORAGE,
                    mock(CanaryConfig.class, RETURNS_DEEP_STUBS),
                    1,
                    mock(CanaryScope.class)))
        .isInstanceOf(RetrofitError.class);

    verify(metricsService, times(ATTEMPTS))
        .queryMetrics(
            anyString(),
            any(CanaryConfig.class),
            any(CanaryMetricConfig.class),
            any(CanaryScope.class));
    verifyZeroInteractions(storageService);
  }

  @Test
  public void retriesRetryableHttpSeriesAndReturnsSuccessfulResponse() throws IOException {
    List response = asList(mock(MetricSet.class));
    when(metricsService.queryMetrics(
            anyString(),
            any(CanaryConfig.class),
            any(CanaryMetricConfig.class),
            any(CanaryScope.class)))
        .thenThrow(getRetrofitErrorWithHttpStatus(INTERNAL_SERVER_ERROR.value()))
        .thenThrow(getRetrofitErrorWithHttpStatus(BAD_GATEWAY.value()))
        .thenThrow(getRetrofitErrorWithHttpStatus(HttpStatus.TEMPORARY_REDIRECT.value()))
        .thenReturn(response);

    processor.executeQuery(
        METRICS, STORAGE, mock(CanaryConfig.class, RETURNS_DEEP_STUBS), 1, mock(CanaryScope.class));

    verify(metricsService, times(4))
        .queryMetrics(
            anyString(),
            any(CanaryConfig.class),
            any(CanaryMetricConfig.class),
            any(CanaryScope.class));
    verify(storageService)
        .storeObject(eq(STORAGE), eq(ObjectType.METRIC_SET_LIST), any(), eq(response));
  }

  @Test
  public void retriesRetryableHttpStatusAndReturnsSuccessfulResponse() throws IOException {
    List response = asList(mock(MetricSet.class));
    when(metricsService.queryMetrics(
            anyString(),
            any(CanaryConfig.class),
            any(CanaryMetricConfig.class),
            any(CanaryScope.class)))
        .thenThrow(getRetrofitErrorWithHttpStatus(LOCKED.value()))
        .thenThrow(getRetrofitErrorWithHttpStatus(LOCKED.value()))
        .thenThrow(getRetrofitErrorWithHttpStatus(LOCKED.value()))
        .thenReturn(response);

    processor.executeQuery(
        METRICS, STORAGE, mock(CanaryConfig.class, RETURNS_DEEP_STUBS), 1, mock(CanaryScope.class));

    verify(metricsService, times(4))
        .queryMetrics(
            anyString(),
            any(CanaryConfig.class),
            any(CanaryMetricConfig.class),
            any(CanaryScope.class));
    verify(storageService)
        .storeObject(eq(STORAGE), eq(ObjectType.METRIC_SET_LIST), any(), eq(response));
  }

  @Test
  public void doesNotRetryNonRetryableHttpStatusAndThrowsException() throws IOException {
    when(metricsService.queryMetrics(
            anyString(),
            any(CanaryConfig.class),
            any(CanaryMetricConfig.class),
            any(CanaryScope.class)))
        .thenThrow(getRetrofitErrorWithHttpStatus(BAD_REQUEST.value()));

    assertThatThrownBy(
            () ->
                processor.executeQuery(
                    METRICS,
                    STORAGE,
                    mock(CanaryConfig.class, RETURNS_DEEP_STUBS),
                    1,
                    mock(CanaryScope.class)))
        .isInstanceOf(RetrofitError.class);

    verify(metricsService, times(1))
        .queryMetrics(
            anyString(),
            any(CanaryConfig.class),
            any(CanaryMetricConfig.class),
            any(CanaryScope.class));
    verifyZeroInteractions(storageService);
  }

  @Test
  public void retriesIoExceptionTillMaxAttemptsAndThrowsException() throws IOException {
    when(metricsService.queryMetrics(
            anyString(),
            any(CanaryConfig.class),
            any(CanaryMetricConfig.class),
            any(CanaryScope.class)))
        .thenThrow(new IOException());

    assertThatThrownBy(
            () ->
                processor.executeQuery(
                    METRICS,
                    STORAGE,
                    mock(CanaryConfig.class, RETURNS_DEEP_STUBS),
                    1,
                    mock(CanaryScope.class)))
        .isInstanceOf(IOException.class);

    verify(metricsService, times(ATTEMPTS))
        .queryMetrics(
            anyString(),
            any(CanaryConfig.class),
            any(CanaryMetricConfig.class),
            any(CanaryScope.class));
    verifyZeroInteractions(storageService);
  }

  private RetrofitError getRetrofitErrorWithHttpStatus(int status) {
    return RetrofitError.httpError(
        "url", new Response("url", status, "reason", Collections.emptyList(), null), null, null);
  }
}
