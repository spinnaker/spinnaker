package com.netflix.kayenta.prometheus.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.metrics.MetricSet;
import com.netflix.kayenta.metrics.MetricSetMixerService;
import com.netflix.kayenta.metrics.MetricSetPair;
import com.netflix.kayenta.prometheus.canary.PrometheusCanaryScope;
import com.netflix.kayenta.prometheus.config.PrometheusManagedAccount;
import com.netflix.kayenta.prometheus.config.PrometheusResponseConverter;
import com.netflix.kayenta.prometheus.metrics.PrometheusMetricsService;
import com.netflix.kayenta.prometheus.model.PrometheusResults;
import com.netflix.kayenta.prometheus.service.PrometheusRemoteService;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.spectator.api.NoopRegistry;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import retrofit2.Converter;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * Integration Test for reproducing and TDD'ing the following issues:
 * https://github.com/spinnaker/kayenta/issues/522 https://github.com/spinnaker/kayenta/issues/524
 */
public class CanaryAnalysisPrometheusMetricsMixerServiceIntegrationTest {

  private static final String ACCOUNT_NAME = "some-prometheus-account";

  @Mock private AccountCredentialsRepository accountCredentialsRepository;

  @Mock PrometheusManagedAccount credentials;

  @Mock PrometheusRemoteService prometheusRemoteService;

  private PrometheusResponseConverter prometheusResponseConverter;

  private PrometheusMetricsService prometheusMetricsService;

  private MetricSetMixerService metricSetMixerService;

  private final Retrofit retrofit =
      new Retrofit.Builder()
          .baseUrl("http://prometheus")
          .addConverterFactory(JacksonConverterFactory.create())
          .build();

  private Converter<ResponseBody, List<PrometheusResults>> prometheusResultsConverter;

  @BeforeEach
  public void before() {
    initMocks(this);
    prometheusResponseConverter = new PrometheusResponseConverter(new ObjectMapper());
    prometheusResultsConverter =
        (Converter<ResponseBody, List<PrometheusResults>>)
            prometheusResponseConverter.responseBodyConverter(
                new ParameterizedTypeImpl(List.class, PrometheusResults.class),
                new Annotation[0],
                retrofit);
    prometheusMetricsService =
        spy(
            PrometheusMetricsService.builder()
                .scopeLabel("instance")
                .accountCredentialsRepository(accountCredentialsRepository)
                .registry(new NoopRegistry())
                .build());

    metricSetMixerService = new MetricSetMixerService();

    when(accountCredentialsRepository.getRequiredOne(anyString())).thenReturn(credentials);
    when(credentials.getPrometheusRemoteService()).thenReturn(prometheusRemoteService);
  }

  @Test
  public void
      test_that_the_metrics_mixer_service_does_not_throw_an_IAE_when_supplied_data_from_prometheus()
          throws Exception {
    CanaryConfig canaryConfig = new CanaryConfig();
    CanaryMetricConfig canaryMetricConfig = CanaryMetricConfig.builder().name("some-name").build();
    String start = "2019-04-11T11:32:53.349Z";
    String end = "2019-04-11T11:35:53.349Z";
    long step = 60L;

    // HTTP GET http://prometheus-k8s.monitoring.svc.cluster.local:9090/api/v1/query_range
    // ?query=avg(requests{version="baseline",http_code="500"})
    // &start=2019-04-11T11:32:53.349Z
    // &end=2019-04-11T11:35:53.349Z
    // &step=60
    PrometheusCanaryScope controlScope = new PrometheusCanaryScope();
    controlScope.setStart(Instant.parse(start));
    controlScope.setEnd(Instant.parse(end));
    controlScope.setStep(step);
    controlScope.setScope("control");

    String controlQuery = "avg(requests{version=\"baseline\",http_code=\"500\"})";
    doReturn(controlQuery)
        .when(prometheusMetricsService)
        .buildQuery(ACCOUNT_NAME, canaryConfig, canaryMetricConfig, controlScope);

    String controlPrometheusResponse =
        "{\"status\":\"success\",\"data\":{\"resultType\":\"matrix\",\"result\":[{\"metric\":{},\"values\":[[1554982493.349,\"45\"],[1554982553.349,\"120\"]]}]}}";

    ResponseBody controlResponseBody =
        ResponseBody.create(
            MediaType.parse("application/json"), controlPrometheusResponse.getBytes());

    @SuppressWarnings("unchecked")
    List<PrometheusResults> controlResults =
        prometheusResultsConverter.convert(controlResponseBody);

    when(prometheusRemoteService.rangeQuery(controlQuery, start, end, step))
        .thenReturn(controlResults);

    List<MetricSet> controlMetricSet =
        prometheusMetricsService.queryMetrics(
            ACCOUNT_NAME, canaryConfig, canaryMetricConfig, controlScope);

    // HTTP GET http://prometheus-k8s.monitoring.svc.cluster.local:9090/api/v1/query_range
    // ?query=avg(requests{version="canary",http_code="500"})
    // &start=2019-04-11T11:32:53.349Z
    // &end=2019-04-11T11:35:53.349Z
    // &step=60
    PrometheusCanaryScope experimentScope = new PrometheusCanaryScope();
    experimentScope.setStart(Instant.parse(start));
    experimentScope.setEnd(Instant.parse(end));
    experimentScope.setStep(step);
    experimentScope.setScope("experiment");

    String experimentQuery = "avg(requests{version=\"canary\",http_code=\"500\"})";
    doReturn(experimentQuery)
        .when(prometheusMetricsService)
        .buildQuery(ACCOUNT_NAME, canaryConfig, canaryMetricConfig, experimentScope);

    String experimentPrometheusResponse =
        "{\"status\":\"success\",\"data\":{\"resultType\":\"matrix\",\"result\":[{\"metric\":{},\"values\":[[1554982493.349,\"124\"],[1554982553.349,\"288\"]]}]}}";
    ResponseBody experimentResponseBody =
        ResponseBody.create(
            MediaType.parse("application/json"), experimentPrometheusResponse.getBytes());

    @SuppressWarnings("unchecked")
    List<PrometheusResults> experimentResults =
        prometheusResultsConverter.convert(experimentResponseBody);
    when(prometheusRemoteService.rangeQuery(experimentQuery, start, end, step))
        .thenReturn(experimentResults);

    List<MetricSet> experimentMetricSet =
        prometheusMetricsService.queryMetrics(
            ACCOUNT_NAME, canaryConfig, canaryMetricConfig, experimentScope);

    // metrics set mixer
    List<MetricSetPair> metricSetPairList =
        metricSetMixerService.mixAll(
            ImmutableList.of(canaryMetricConfig), controlMetricSet, experimentMetricSet);

    assertNotNull(metricSetPairList);
    assertEquals(1, metricSetPairList.size());
  }

  private static class ParameterizedTypeImpl implements ParameterizedType {
    private final Type rawType;
    private final Type[] typeArguments;

    public ParameterizedTypeImpl(Type rawType, Type... typeArguments) {
      this.rawType = rawType;
      this.typeArguments = typeArguments;
    }

    @Override
    public Type[] getActualTypeArguments() {
      return typeArguments;
    }

    @Override
    public Type getRawType() {
      return rawType;
    }

    @Override
    public Type getOwnerType() {
      return null;
    }
  }
}
