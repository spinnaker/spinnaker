package com.netflix.kayenta.stackdriver.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.api.services.monitoring.v3.Monitoring;
import com.google.api.services.monitoring.v3.model.ListMetricDescriptorsResponse;
import com.google.api.services.monitoring.v3.model.ListTimeSeriesResponse;
import com.google.api.services.monitoring.v3.model.MetricDescriptor;
import com.google.api.services.monitoring.v3.model.Point;
import com.google.api.services.monitoring.v3.model.TimeInterval;
import com.google.api.services.monitoring.v3.model.TimeSeries;
import com.google.api.services.monitoring.v3.model.TypedValue;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.providers.metrics.StackdriverCanaryMetricSetQueryConfig;
import com.netflix.kayenta.google.security.GoogleNamedAccountCredentials;
import com.netflix.kayenta.metrics.MetricSet;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.stackdriver.canary.StackdriverCanaryScope;
import com.netflix.spectator.api.DefaultRegistry;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class StackdriverMetricsServiceTest {

  private static final String ACCOUNT = "test-account";

  private AccountCredentialsRepository accountCredentialsRepoMock;

  private StackdriverMetricsService stackdriverMetricsService;

  @BeforeEach
  void setup() {
    StackdriverMetricsService.StackdriverMetricsServiceBuilder stackdriverMetricsServiceBuilder =
        StackdriverMetricsService.builder();
    accountCredentialsRepoMock = mock(AccountCredentialsRepository.class);
    stackdriverMetricsServiceBuilder
        .accountCredentialsRepository(accountCredentialsRepoMock)
        .registry(new DefaultRegistry());
    stackdriverMetricsService = stackdriverMetricsServiceBuilder.build();
  }

  @Test
  void readsInt64Metrics() throws IOException {
    GoogleNamedAccountCredentials stackdriverCredentialsMock =
        mock(GoogleNamedAccountCredentials.class);
    when(accountCredentialsRepoMock.getRequiredOne(ACCOUNT)).thenReturn(stackdriverCredentialsMock);

    Monitoring monitoringMock = mock(Monitoring.class, Mockito.RETURNS_DEEP_STUBS);
    when(stackdriverCredentialsMock.getMonitoring()).thenReturn(monitoringMock);

    Monitoring.Projects.TimeSeries.List timeSeriesListMock =
        mock(Monitoring.Projects.TimeSeries.List.class);

    when(monitoringMock
            .projects()
            .timeSeries()
            .list(anyString())
            .setAggregationAlignmentPeriod(anyString())
            .setAggregationCrossSeriesReducer(anyString())
            .setAggregationPerSeriesAligner(anyString())
            .setFilter(anyString())
            .setIntervalStartTime(anyString())
            .setIntervalEndTime(anyString()))
        .thenReturn(timeSeriesListMock);

    ListTimeSeriesResponse responseMock = mock(ListTimeSeriesResponse.class);
    when(timeSeriesListMock.execute()).thenReturn(responseMock);

    List<TimeSeries> timeSeriesListWithInt64Points = new ArrayList<TimeSeries>();

    // Create a time series with INT64 points
    List<Point> int64Points = new ArrayList<Point>();
    int64Points.add(
        new Point()
            .setValue(new TypedValue().setInt64Value((Long) 64l))
            .setInterval(
                new TimeInterval()
                    .setStartTime("1970-01-01T00:00:00.00Z")
                    .setEndTime("1970-01-01T00:00:01.00Z")));
    TimeSeries timeSeriesWithInt64Points =
        new TimeSeries().setValueType("INT64").setPoints(int64Points);
    timeSeriesListWithInt64Points.add(timeSeriesWithInt64Points);

    when(responseMock.getTimeSeries()).thenReturn(timeSeriesListWithInt64Points);

    CanaryConfig canaryConfig = new CanaryConfig();
    CanaryMetricConfig canaryMetricConfig =
        CanaryMetricConfig.builder()
            .name("metricConfig")
            .query(
                StackdriverCanaryMetricSetQueryConfig.builder()
                    .resourceType("global")
                    .metricType("instance")
                    .build())
            .build();

    StackdriverCanaryScope canaryScope = new StackdriverCanaryScope();
    canaryScope.setStart(Instant.EPOCH).setEnd(Instant.EPOCH.plusSeconds(1)).setStep(1l);
    canaryScope.setProject("my-project");
    List<MetricSet> queriedMetrics =
        stackdriverMetricsService.queryMetrics(
            ACCOUNT, canaryConfig, canaryMetricConfig, canaryScope);

    assertThat(queriedMetrics.get(0).getValues()).contains(64d);
  }

  @Test
  void returnsSingleMetricDescriptorInCache() throws IOException {
    GoogleNamedAccountCredentials googleAccountCredentialsMock =
        mock(GoogleNamedAccountCredentials.class, Mockito.RETURNS_DEEP_STUBS);

    Set<AccountCredentials> accountCredentialsSetMock = new HashSet<>();
    accountCredentialsSetMock.add(googleAccountCredentialsMock);

    when(accountCredentialsRepoMock.getAllOf(AccountCredentials.Type.METRICS_STORE))
        .thenReturn(accountCredentialsSetMock);

    ListMetricDescriptorsResponse listMetricDescriptorsResponseMock =
        mock(ListMetricDescriptorsResponse.class);
    when(googleAccountCredentialsMock
            .getMonitoring()
            .projects()
            .metricDescriptors()
            .list(anyString())
            .execute())
        .thenReturn(listMetricDescriptorsResponseMock);

    List<MetricDescriptor> metricDesciprtorMockList = new ArrayList<MetricDescriptor>();

    MetricDescriptor exampleMetricDescriptor = new MetricDescriptor();
    metricDesciprtorMockList.add(exampleMetricDescriptor);
    when(listMetricDescriptorsResponseMock.getMetricDescriptors())
        .thenReturn(metricDesciprtorMockList);

    stackdriverMetricsService.updateMetricDescriptorsCache();

    List<Map> metadata = stackdriverMetricsService.getMetadata(ACCOUNT, "");

    assertThat(metadata).containsOnly(exampleMetricDescriptor);
  }
}
