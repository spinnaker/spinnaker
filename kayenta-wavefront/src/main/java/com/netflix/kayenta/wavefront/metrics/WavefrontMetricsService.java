/*
 * Copyright 2019 Intuit, Inc.
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
package com.netflix.kayenta.wavefront.metrics;

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.providers.metrics.WavefrontCanaryMetricSetQueryConfig;
import com.netflix.kayenta.wavefront.canary.WavefrontCanaryScope;
import com.netflix.kayenta.wavefront.canary.WavefrontCanaryScopeFactory;
import com.netflix.kayenta.wavefront.service.WavefrontTimeSeries;
import com.netflix.kayenta.wavefront.security.WavefrontCredentials;
import com.netflix.kayenta.wavefront.security.WavefrontNamedAccountCredentials;
import com.netflix.kayenta.wavefront.service.WavefrontRemoteService;
import com.netflix.kayenta.metrics.MetricSet;
import com.netflix.kayenta.metrics.MetricsService;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.spectator.api.Registry;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Builder
@Slf4j
public class WavefrontMetricsService implements MetricsService {

    @NotNull
    @Singular
    @Getter
    private List<String> accountNames;

    @Autowired
    private final AccountCredentialsRepository accountCredentialsRepository;

    @Autowired
    private final Registry registry;

    @Override
    public String getType() {
        return WavefrontCanaryMetricSetQueryConfig.SERVICE_TYPE;
    }

    @Override
    public boolean servicesAccount(String accountName) {
        return accountNames.contains(accountName);
    }

    @Override
    public String buildQuery(String metricsAccountName,
                             CanaryConfig canaryConfig,
                             CanaryMetricConfig canaryMetricConfig,
                             CanaryScope canaryScope) {
        WavefrontCanaryMetricSetQueryConfig queryConfig = (WavefrontCanaryMetricSetQueryConfig)canaryMetricConfig.getQuery();

        String query = queryConfig.getMetricName();
        if (canaryScope.getScope() != null && !canaryScope.getScope().equals("")) {
            query = query + ", " + canaryScope.getScope();
        }

        query = "ts(" + query + ")";
        if (queryConfig.getAggregate() != null && !queryConfig.getAggregate().equals("")) {
            query = queryConfig.getAggregate() + "(" + query + ")";
        }

        return query;
    }

    @Override
    public List<MetricSet> queryMetrics(String accountName,
                                        CanaryConfig canaryConfig,
                                        CanaryMetricConfig canaryMetricConfig,
                                        CanaryScope canaryScope) throws IOException {
        WavefrontCanaryScope wavefrontCanaryScope = (WavefrontCanaryScope) canaryScope;
        WavefrontNamedAccountCredentials accountCredentials = (WavefrontNamedAccountCredentials) accountCredentialsRepository
                .getOne(accountName)
                .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + accountName + "."));
        WavefrontCredentials credentials = accountCredentials.getCredentials();
        WavefrontRemoteService wavefrontRemoteService = accountCredentials.getWavefrontRemoteService();

        WavefrontCanaryMetricSetQueryConfig queryConfig = (WavefrontCanaryMetricSetQueryConfig)canaryMetricConfig.getQuery();
        String query = buildQuery(accountName,
                canaryConfig,
                canaryMetricConfig,
                canaryScope);

        WavefrontTimeSeries timeSeries = wavefrontRemoteService.fetch(
                credentials.getApiToken(),
                "Kayenta-Query",
                query,
                wavefrontCanaryScope.getStart().toEpochMilli(),
                wavefrontCanaryScope.getEnd().toEpochMilli(),
                wavefrontCanaryScope.getGranularity(),
                queryConfig.getSummarization(),
                true, //listMode
                true, //strict
                true);//sorted

        List<MetricSet> metricSetList = new ArrayList<>();

        if (timeSeries.getWarnings() != null) {
            log.warn(timeSeries.getWarnings());
        }
        if (timeSeries.getTimeSeries() == null) {
            throw new IllegalStateException("No metrics returned for query: " + timeSeries.getQuery() + " from " + wavefrontCanaryScope.getStart() + " to " + wavefrontCanaryScope.getEnd() );
        }
        for (WavefrontTimeSeries.WavefrontSeriesEntry series: timeSeries.getTimeSeries()) {
            List<List<Number>> seriesDate = series.getData();
            if (seriesDate.size() == 0) {
                log.warn("No metrics found for label: " + series.getLabel() +" under host: " + series.getHost());
                continue;
            }
            Long responseStartTime = seriesDate.get(0).get(0).longValue();
            Long responseEndTime = seriesDate.get(seriesDate.size() - 1).get(0).longValue();
            MetricSet.MetricSetBuilder metricSetBuilder = MetricSet.builder()
                .name(canaryMetricConfig.getName())
                .startTimeMillis(TimeUnit.SECONDS.toMillis(responseStartTime))
                .startTimeIso(Instant.ofEpochSecond(responseStartTime).toString())
                .endTimeMillis(TimeUnit.SECONDS.toMillis(responseEndTime))
                .endTimeIso(Instant.ofEpochSecond(responseEndTime).toString())
                .stepMillis(TimeUnit.SECONDS.toMillis(canaryScope.getStep()))
                .values(series.getDataPoints(canaryScope.getStep()).collect(Collectors.toList()));

            Map<String, String> tags = series.getTags();

            if (tags != null) {
                metricSetBuilder.tags(tags);
            }
            metricSetBuilder.attribute("query", query);
            metricSetBuilder.attribute("summarization", queryConfig.getSummarization());
            metricSetList.add(metricSetBuilder.build());
        }
        return metricSetList;
    }
}
