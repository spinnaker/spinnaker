/*
 * Copyright 2019 Google, LLC
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

package com.netflix.spinnaker.clouddriver.google.provider.agent;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static org.assertj.core.api.Assertions.entry;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonErrorContainer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A factory for {@link Compute} instances that handle (a subset of) Google Compute Engine API
 * requests with a pre-specified set of objects.
 */
// At some point this could be turned into a proper fake by supporting POST requests for adding
// data, etc. Then we could use it for testing a lot more things, like tasks. For now, just
// providing simpler methods like setInstances() and using it more like a stub seems easier.
final class StubComputeFactory {

  private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

  private static final String COMPUTE_PATH_PREFIX = "/compute/[-.a-zA-Z0-9]+";

  private static final String COMPUTE_PROJECT_PATH_PREFIX =
      COMPUTE_PATH_PREFIX + "/projects/[-.a-zA-Z0-9]+";

  private static final Pattern BATCH_COMPUTE_PATTERN =
      Pattern.compile("/batch/compute/[-.a-zA-Z0-9]+");

  private static final Pattern GET_ZONAL_IGM_PATTERN =
      Pattern.compile(
          COMPUTE_PROJECT_PATH_PREFIX
              + "/zones/([-a-z0-9]+)/instanceGroupManagers/([-a-zA-Z0-9]+)");
  private static final Pattern LIST_ZONAL_IGM_PATTERN =
      Pattern.compile(COMPUTE_PROJECT_PATH_PREFIX + "/zones/([-a-z0-9]+)/instanceGroupManagers");

  private static final Pattern GET_REGIONAL_IGM_PATTERN =
      Pattern.compile(
          COMPUTE_PROJECT_PATH_PREFIX
              + "/regions/([-a-z0-9]+)/instanceGroupManagers/([-a-zA-Z0-9]+)");
  private static final Pattern LIST_REGIONAL_IGM_PATTERN =
      Pattern.compile(COMPUTE_PROJECT_PATH_PREFIX + "/regions/([-a-z0-9]+)/instanceGroupManagers");

  private static final Pattern GET_INSTANCE_TEMPLATE_PATTERN =
      Pattern.compile(COMPUTE_PROJECT_PATH_PREFIX + "/global/instanceTemplates/([-a-zA-Z0-9]+)");
  private static final Pattern LIST_INSTANCE_TEMPLATES_PATTERN =
      Pattern.compile(COMPUTE_PROJECT_PATH_PREFIX + "/global/instanceTemplates");

  private static final Pattern LIST_ZONAL_INSTANCES_PATTERN =
      Pattern.compile(COMPUTE_PROJECT_PATH_PREFIX + "/zones/([-a-z0-9]+)/instances");
  private static final Pattern AGGREGATED_INSTANCES_PATTERN =
      Pattern.compile(COMPUTE_PROJECT_PATH_PREFIX + "/aggregated/instances");

  private static final Pattern GET_ZONAL_AUTOSCALER_PATTERN =
      Pattern.compile(
          COMPUTE_PROJECT_PATH_PREFIX + "/zones/([-a-z0-9]+)/autoscalers/([-a-zA-Z0-9]+)");
  private static final Pattern LIST_ZONAL_AUTOSCALERS_PATTERN =
      Pattern.compile(COMPUTE_PROJECT_PATH_PREFIX + "/zones/([-a-z0-9]+)/autoscalers");
  private static final Pattern GET_REGIONAL_AUTOSCALER_PATTERN =
      Pattern.compile(
          COMPUTE_PROJECT_PATH_PREFIX + "/regions/([-a-z0-9]+)/autoscalers/([-a-zA-Z0-9]+)");
  private static final Pattern LIST_REGIONAL_AUTOSCALERS_PATTERN =
      Pattern.compile(COMPUTE_PROJECT_PATH_PREFIX + "/regions/([-a-z0-9]+)/autoscalers");
  private static final Pattern AGGREGATED_AUTOSCALERS_PATTERN =
      Pattern.compile(COMPUTE_PROJECT_PATH_PREFIX + "/aggregated/autoscalers");
  private static final Pattern GET_PROJECT_PATTERN =
      Pattern.compile(COMPUTE_PATH_PREFIX + "/projects/([-.a-zA-Z0-9]+)");

  private List<InstanceGroupManager> instanceGroupManagers = new ArrayList<>();
  private List<InstanceTemplate> instanceTemplates = new ArrayList<>();
  private List<Instance> instances = new ArrayList<>();
  private List<Autoscaler> autoscalers = new ArrayList<>();
  private List<Project> projects = new ArrayList<>();
  private Exception projectException;

  StubComputeFactory setInstanceGroupManagers(InstanceGroupManager... instanceGroupManagers) {
    this.instanceGroupManagers = ImmutableList.copyOf(instanceGroupManagers);
    return this;
  }

  StubComputeFactory setInstanceTemplates(InstanceTemplate... instanceTemplates) {
    this.instanceTemplates = ImmutableList.copyOf(instanceTemplates);
    return this;
  }

  StubComputeFactory setInstances(Instance... instances) {
    this.instances = ImmutableList.copyOf(instances);
    return this;
  }

  StubComputeFactory setAutoscalers(Autoscaler... autoscalers) {
    this.autoscalers = ImmutableList.copyOf(autoscalers);
    return this;
  }

  StubComputeFactory setProjects(Project... projects) {
    this.projects = ImmutableList.copyOf(projects);
    return this;
  }

  StubComputeFactory setProjectException(Exception projectException) {
    this.projectException = projectException;
    return this;
  }

  Compute create() {
    HttpTransport httpTransport =
        new StubHttpTransport()
            .addBatchRequestHandlerForPath(BATCH_COMPUTE_PATTERN)
            .addGetResponse(GET_ZONAL_IGM_PATTERN, this::getInstanceGroupManager)
            .addGetResponse(
                LIST_ZONAL_IGM_PATTERN,
                new PathBasedJsonResponseGenerator(this::instanceGroupManagerList))
            .addGetResponse(GET_REGIONAL_IGM_PATTERN, this::getRegionInstanceGroupManager)
            .addGetResponse(
                LIST_REGIONAL_IGM_PATTERN,
                new PathBasedJsonResponseGenerator(this::regionInstanceGroupManagerList))
            .addGetResponse(GET_INSTANCE_TEMPLATE_PATTERN, this::getInstanceTemplate)
            .addGetResponse(LIST_INSTANCE_TEMPLATES_PATTERN, this::instanceTemplateList)
            .addGetResponse(
                LIST_ZONAL_INSTANCES_PATTERN,
                new PathBasedJsonResponseGenerator(this::instanceList))
            .addGetResponse(AGGREGATED_INSTANCES_PATTERN, this::instanceAggregatedList)
            .addGetResponse(GET_ZONAL_AUTOSCALER_PATTERN, this::getZonalAutoscaler)
            .addGetResponse(
                LIST_ZONAL_AUTOSCALERS_PATTERN,
                new PathBasedJsonResponseGenerator(this::zonalAutoscalerList))
            .addGetResponse(GET_REGIONAL_AUTOSCALER_PATTERN, this::getRegionalAutoscaler)
            .addGetResponse(
                LIST_REGIONAL_AUTOSCALERS_PATTERN,
                new PathBasedJsonResponseGenerator(this::regionalAutoscalerList))
            .addGetResponse(AGGREGATED_AUTOSCALERS_PATTERN, this::autoscalerAggregatedList)
            .addGetResponse(GET_PROJECT_PATTERN, this::project);
    return new Compute(
        httpTransport, GsonFactory.getDefaultInstance(), /* httpRequestInitializer= */ null);
  }

  private MockLowLevelHttpResponse getInstanceGroupManager(MockLowLevelHttpRequest request) {
    Matcher matcher = GET_ZONAL_IGM_PATTERN.matcher(getPath(request));
    checkState(matcher.matches());
    String zone = matcher.group(1);
    String name = matcher.group(2);
    return instanceGroupManagers.stream()
        .filter(igm -> name.equals(igm.getName()))
        .filter(igm -> zone.equals(Utils.getLocalName(igm.getZone())))
        .findFirst()
        .map(StubComputeFactory::jsonResponse)
        .orElse(errorResponse(404));
  }

  private InstanceGroupManagerList instanceGroupManagerList(String path) {
    Matcher matcher = LIST_ZONAL_IGM_PATTERN.matcher(path);
    checkState(matcher.matches());
    String zone = matcher.group(1);
    return new InstanceGroupManagerList()
        .setItems(
            instanceGroupManagers.stream()
                .filter(igm -> zone.equals(Utils.getLocalName(igm.getZone())))
                .collect(toImmutableList()));
  }

  private MockLowLevelHttpResponse getRegionInstanceGroupManager(MockLowLevelHttpRequest request) {
    Matcher matcher = GET_REGIONAL_IGM_PATTERN.matcher(getPath(request));
    checkState(matcher.matches());
    String region = matcher.group(1);
    String name = matcher.group(2);
    return instanceGroupManagers.stream()
        .filter(igm -> name.equals(igm.getName()))
        .filter(igm -> region.equals(Utils.getLocalName(igm.getRegion())))
        .findFirst()
        .map(StubComputeFactory::jsonResponse)
        .orElse(errorResponse(404));
  }

  private RegionInstanceGroupManagerList regionInstanceGroupManagerList(String path) {
    Matcher matcher = LIST_REGIONAL_IGM_PATTERN.matcher(path);
    checkState(matcher.matches());
    String region = matcher.group(1);
    return new RegionInstanceGroupManagerList()
        .setItems(
            instanceGroupManagers.stream()
                .filter(igm -> region.equals(Utils.getLocalName(igm.getRegion())))
                .collect(toImmutableList()));
  }

  private MockLowLevelHttpResponse getInstanceTemplate(MockLowLevelHttpRequest request) {
    Matcher matcher = GET_INSTANCE_TEMPLATE_PATTERN.matcher(getPath(request));
    checkState(matcher.matches());
    String name = matcher.group(1);
    return instanceTemplates.stream()
        .filter(template -> name.equals(template.getName()))
        .findFirst()
        .map(StubComputeFactory::jsonResponse)
        .orElse(errorResponse(404));
  }

  private MockLowLevelHttpResponse instanceTemplateList(LowLevelHttpRequest request) {
    return jsonResponse(new InstanceTemplateList().setItems(instanceTemplates));
  }

  private InstanceList instanceList(String path) {
    Matcher matcher = LIST_ZONAL_INSTANCES_PATTERN.matcher(path);
    checkState(matcher.matches());
    String zone = matcher.group(1);
    return new InstanceList()
        .setItems(
            instances.stream()
                .filter(instance -> zone.equals(Utils.getLocalName(instance.getZone())))
                .collect(toImmutableList()));
  }

  private MockLowLevelHttpResponse instanceAggregatedList(LowLevelHttpRequest request) {
    ImmutableListMultimap<String, Instance> instancesMultimap =
        aggregate(instances, Instance::getZone, /* regionFunction= */ instance -> null);
    ImmutableMap<String, InstancesScopedList> instances =
        instancesMultimap.asMap().entrySet().stream()
            .collect(
                toImmutableMap(
                    Map.Entry::getKey,
                    e ->
                        new InstancesScopedList()
                            .setInstances(ImmutableList.copyOf(e.getValue()))));
    InstanceAggregatedList result = new InstanceAggregatedList().setItems(instances);
    return jsonResponse(result);
  }

  private MockLowLevelHttpResponse getZonalAutoscaler(MockLowLevelHttpRequest request) {
    Matcher matcher = GET_ZONAL_AUTOSCALER_PATTERN.matcher(getPath(request));
    checkState(matcher.matches());
    String zone = matcher.group(1);
    String name = matcher.group(2);
    return autoscalers.stream()
        .filter(autoscaler -> name.equals(autoscaler.getName()))
        .filter(autoscaler -> zone.equals(Utils.getLocalName(autoscaler.getZone())))
        .findFirst()
        .map(StubComputeFactory::jsonResponse)
        .orElse(errorResponse(404));
  }

  private AutoscalerList zonalAutoscalerList(String path) {
    Matcher matcher = LIST_ZONAL_AUTOSCALERS_PATTERN.matcher(path);
    checkState(matcher.matches());
    String zone = matcher.group(1);
    return new AutoscalerList()
        .setItems(
            autoscalers.stream()
                .filter(autoscaler -> zone.equals(Utils.getLocalName(autoscaler.getZone())))
                .collect(toImmutableList()));
  }

  private MockLowLevelHttpResponse getRegionalAutoscaler(MockLowLevelHttpRequest request) {
    Matcher matcher = GET_REGIONAL_AUTOSCALER_PATTERN.matcher(getPath(request));
    checkState(matcher.matches());
    String region = matcher.group(1);
    String name = matcher.group(2);
    return autoscalers.stream()
        .filter(autoscaler -> name.equals(autoscaler.getName()))
        .filter(autoscaler -> region.equals(Utils.getLocalName(autoscaler.getRegion())))
        .findFirst()
        .map(StubComputeFactory::jsonResponse)
        .orElse(errorResponse(404));
  }

  private AutoscalerList regionalAutoscalerList(String path) {
    Matcher matcher = LIST_REGIONAL_AUTOSCALERS_PATTERN.matcher(path);
    checkState(matcher.matches());
    String region = matcher.group(1);
    return new AutoscalerList()
        .setItems(
            autoscalers.stream()
                .filter(autoscaler -> region.equals(Utils.getLocalName(autoscaler.getRegion())))
                .collect(toImmutableList()));
  }

  private MockLowLevelHttpResponse autoscalerAggregatedList(LowLevelHttpRequest request) {
    ImmutableListMultimap<String, Autoscaler> autoscalersMultimap =
        aggregate(autoscalers, Autoscaler::getZone, Autoscaler::getRegion);
    ImmutableMap<String, AutoscalersScopedList> autoscalers =
        autoscalersMultimap.asMap().entrySet().stream()
            .collect(
                toImmutableMap(
                    Map.Entry::getKey,
                    e ->
                        new AutoscalersScopedList()
                            .setAutoscalers(ImmutableList.copyOf(e.getValue()))));
    return jsonResponse(new AutoscalerAggregatedList().setItems(autoscalers));
  }

  private MockLowLevelHttpResponse project(MockLowLevelHttpRequest request) {
    if (projectException != null) {
      return errorResponse(500, projectException);
    }

    Matcher matcher = GET_PROJECT_PATTERN.matcher(getPath(request));
    checkState(matcher.matches());
    String name = matcher.group(1);
    return projects.stream()
        .filter(project -> name.equals(project.getName()))
        .findFirst()
        .map(StubComputeFactory::jsonResponse)
        .orElse(errorResponse(404));
  }

  private static <T> ImmutableListMultimap<String, T> aggregate(
      Collection<T> items, Function<T, String> zoneFunction, Function<T, String> regionFunction) {
    return items.stream()
        .map(item -> entry(getAggregateKey(item, zoneFunction, regionFunction), item))
        .filter(entry -> entry.getKey() != null)
        .collect(toImmutableListMultimap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private static <T> String getAggregateKey(
      T item, Function<T, String> zoneFunction, Function<T, String> regionFunction) {

    String zone = zoneFunction.apply(item);
    if (zone != null) {
      return "zones/" + Utils.getLocalName(zone);
    }
    String region = regionFunction.apply(item);
    if (region != null) {
      return "regions/" + Utils.getLocalName(region);
    }
    return null;
  }

  private static MockLowLevelHttpResponse errorResponse(int statusCode) {
    return errorResponse(statusCode, null);
  }

  private static MockLowLevelHttpResponse errorResponse(int statusCode, Exception exception) {
    GoogleJsonErrorContainer errorContainer = new GoogleJsonErrorContainer();
    GoogleJsonError error = new GoogleJsonError();
    error.setCode(statusCode);

    if (exception != null) {
      error.setMessage(exception.getMessage());
    }

    errorContainer.setError(error);
    return jsonResponse(statusCode, errorContainer);
  }

  private static MockLowLevelHttpResponse jsonResponse(GenericJson jsonObject) {
    return jsonResponse(200, jsonObject);
  }

  private static MockLowLevelHttpResponse jsonResponse(int statusCode, GenericJson jsonObject) {
    try {
      return new MockLowLevelHttpResponse()
          .setStatusCode(statusCode)
          .setContent(JSON_FACTORY.toByteArray(jsonObject));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static class PathBasedJsonResponseGenerator
      implements Function<MockLowLevelHttpRequest, MockLowLevelHttpResponse> {

    final Function<String, GenericJson> responseGenerator;

    private PathBasedJsonResponseGenerator(Function<String, GenericJson> responseGenerator) {
      this.responseGenerator = responseGenerator;
    }

    @Override
    public MockLowLevelHttpResponse apply(MockLowLevelHttpRequest request) {
      GenericJson output = responseGenerator.apply(getPath(request));
      return jsonResponse(output);
    }
  }

  private static String getPath(MockLowLevelHttpRequest request) {
    try {
      return new URL(request.getUrl()).getPath();
    } catch (MalformedURLException e) {
      throw new IllegalStateException(e);
    }
  }
}
