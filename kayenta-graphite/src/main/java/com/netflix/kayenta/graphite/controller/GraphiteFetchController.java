/*
 * Copyright 2018 Snap Inc.
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

package com.netflix.kayenta.graphite.controller;

import static com.netflix.kayenta.canary.util.FetchControllerUtils.determineDefaultProperty;

import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.providers.metrics.GraphiteCanaryMetricSetQueryConfig;
import com.netflix.kayenta.graphite.config.GraphiteConfigurationTestControllerDefaultProperties;
import com.netflix.kayenta.metrics.SynchronousQueryProcessor;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.CredentialsHelper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/fetch/graphite")
@Slf4j
public class GraphiteFetchController {
    private final AccountCredentialsRepository accountCredentialsRepository;
    private final SynchronousQueryProcessor synchronousQueryProcessor;
    private final GraphiteConfigurationTestControllerDefaultProperties
        graphiteConfigurationTestControllerDefaultProperties;

    @Autowired
    public GraphiteFetchController(AccountCredentialsRepository accountCredentialsRepository,
        SynchronousQueryProcessor synchronousQueryProcessor,
        GraphiteConfigurationTestControllerDefaultProperties graphiteConfigurationTestControllerDefaultProperties) {
        this.accountCredentialsRepository = accountCredentialsRepository;
        this.synchronousQueryProcessor = synchronousQueryProcessor;
        this.graphiteConfigurationTestControllerDefaultProperties =
            graphiteConfigurationTestControllerDefaultProperties;
    }

    @RequestMapping(value = "/query", method = RequestMethod.POST)
    public Map queryMetrics(@RequestParam(required = false) final String metricsAccountName,
        @RequestParam(required = false) final String storageAccountName,
        @ApiParam(defaultValue = "cpu") @RequestParam String metricSetName,
        @ApiParam(defaultValue = "system.$location.$scope") @RequestParam String metricName,
        @ApiParam(value = "The name of the resource to use when scoping the query. " +
            "This parameter will replace $scope in metricName")
        @RequestParam(required = false) String scope,
        @ApiParam(value = "The name of the resource to use when locating the query. " +
            "This parameter will replace $location in metricName")
        @RequestParam(required = false) String location,
        @ApiParam(value = "An ISO format timestamp, e.g.: 2018-03-15T01:23:45Z")
        @RequestParam String start,
        @ApiParam(value = "An ISO format timestamp, e.g.: 2018-03-15T01:23:45Z")
        @RequestParam String end,
        @ApiParam(defaultValue = "false")
        @RequestParam(required = false) final boolean dryRun) throws IOException {

        start = determineDefaultProperty(start, "start", graphiteConfigurationTestControllerDefaultProperties);
        end = determineDefaultProperty(end, "end", graphiteConfigurationTestControllerDefaultProperties);
        scope = determineDefaultProperty(scope, "scope", graphiteConfigurationTestControllerDefaultProperties);

        if (StringUtils.isEmpty(start)) {
            throw new IllegalArgumentException("Start time is required.");
        }

        if (StringUtils.isEmpty(end)) {
            throw new IllegalArgumentException("End time is required.");
        }

        String resolvedMetricsAccountName = CredentialsHelper.resolveAccountByNameOrType(metricsAccountName,
            AccountCredentials.Type.METRICS_STORE,
            accountCredentialsRepository);
        String resolvedStorageAccountName = CredentialsHelper.resolveAccountByNameOrType(storageAccountName,
            AccountCredentials.Type.OBJECT_STORE,
            accountCredentialsRepository);

        GraphiteCanaryMetricSetQueryConfig.GraphiteCanaryMetricSetQueryConfigBuilder
            graphiteCanaryMetricSetQueryConfigBuilder = GraphiteCanaryMetricSetQueryConfig.builder();

        graphiteCanaryMetricSetQueryConfigBuilder.metricName(metricName);

        CanaryMetricConfig canaryMetricConfig =
            CanaryMetricConfig.builder()
                .name(metricSetName)
                .query(graphiteCanaryMetricSetQueryConfigBuilder.build())
                .build();

        CanaryScope canaryScope = new CanaryScope(scope, location, Instant.parse(start),
            Instant.parse(end), null, Collections.EMPTY_MAP);

        return synchronousQueryProcessor.processQueryAndReturnMap(
            resolvedMetricsAccountName,
            resolvedStorageAccountName,
            null,
            canaryMetricConfig,
            0,
            canaryScope,
            dryRun);
    }
}
