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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerNetworkException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import retrofit.RetrofitError;
import retrofit.client.Response;

@ExtendWith(MockitoExtension.class)
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

  @BeforeEach
  public void setUp() {
    when(metricsServiceRepository.getRequiredOne(METRICS)).thenReturn(metricsService);
    when(storageServiceRepository.getRequiredOne(STORAGE)).thenReturn(storageService);
    lenient().when(retryConfiguration.getAttempts()).thenReturn(ATTEMPTS);
    lenient()
        .when(retryConfiguration.getSeries())
        .thenReturn(
            new HashSet<>(
                Arrays.asList(HttpStatus.Series.SERVER_ERROR, HttpStatus.Series.REDIRECTION)));
    lenient()
        .when(retryConfiguration.getStatuses())
        .thenReturn(new HashSet<>(Arrays.asList(LOCKED)));
  }

  @Test
  public void retriesRetryableHttpSeriesTillMaxAttemptsAndThrowsException() throws IOException {
    when(metricsService.queryMetrics(
            anyString(),
            any(CanaryConfig.class),
            any(CanaryMetricConfig.class),
            any(CanaryScope.class)))
        .thenThrow(getSpinnakerHttpException(INTERNAL_SERVER_ERROR.value()));

    assertThatThrownBy(
            () ->
                processor.executeQuery(
                    METRICS,
                    STORAGE,
                    mock(CanaryConfig.class, RETURNS_DEEP_STUBS),
                    1,
                    mock(CanaryScope.class)))
        .isInstanceOf(SpinnakerHttpException.class);

    verify(metricsService, times(ATTEMPTS))
        .queryMetrics(
            anyString(),
            any(CanaryConfig.class),
            any(CanaryMetricConfig.class),
            any(CanaryScope.class));
    verifyNoMoreInteractions(storageService);
  }

  @Test
  public void retriesRetryableHttpSeriesAndReturnsSuccessfulResponse() throws IOException {
    List response = asList(mock(MetricSet.class));
    when(metricsService.queryMetrics(
            anyString(),
            any(CanaryConfig.class),
            any(CanaryMetricConfig.class),
            any(CanaryScope.class)))
        .thenThrow(getSpinnakerHttpException(INTERNAL_SERVER_ERROR.value()))
        .thenThrow(getSpinnakerHttpException(BAD_GATEWAY.value()))
        .thenThrow(getSpinnakerHttpException(HttpStatus.TEMPORARY_REDIRECT.value()))
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
        .thenThrow(getSpinnakerHttpException(LOCKED.value()))
        .thenThrow(getSpinnakerHttpException(LOCKED.value()))
        .thenThrow(getSpinnakerHttpException(LOCKED.value()))
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
        .thenThrow(getSpinnakerHttpException(BAD_REQUEST.value()));

    assertThatThrownBy(
            () ->
                processor.executeQuery(
                    METRICS,
                    STORAGE,
                    mock(CanaryConfig.class, RETURNS_DEEP_STUBS),
                    1,
                    mock(CanaryScope.class)))
        .isInstanceOf(SpinnakerHttpException.class);

    verify(metricsService, times(1))
        .queryMetrics(
            anyString(),
            any(CanaryConfig.class),
            any(CanaryMetricConfig.class),
            any(CanaryScope.class));
    verifyNoMoreInteractions(storageService);
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
    verifyNoMoreInteractions(storageService);
  }

  @Test
  public void retriesNetworkErrorTillMaxAttemptsAndThrowsException() throws IOException {
    when(metricsService.queryMetrics(
            anyString(),
            any(CanaryConfig.class),
            any(CanaryMetricConfig.class),
            any(CanaryScope.class)))
        .thenThrow(
            new SpinnakerNetworkException(
                RetrofitError.networkError("http://some-url", new SocketTimeoutException())));

    assertThatThrownBy(
            () ->
                processor.executeQuery(
                    METRICS,
                    STORAGE,
                    mock(CanaryConfig.class, RETURNS_DEEP_STUBS),
                    1,
                    mock(CanaryScope.class)))
        .isInstanceOf(SpinnakerNetworkException.class);

    verify(metricsService, times(ATTEMPTS))
        .queryMetrics(
            anyString(),
            any(CanaryConfig.class),
            any(CanaryMetricConfig.class),
            any(CanaryScope.class));
    verifyNoMoreInteractions(storageService);
  }

  private SpinnakerHttpException getSpinnakerHttpException(int status) {
    return new SpinnakerHttpException(
        RetrofitError.httpError(
            "url",
            new Response("url", status, "reason", Collections.emptyList(), null),
            null,
            null));
  }
}
