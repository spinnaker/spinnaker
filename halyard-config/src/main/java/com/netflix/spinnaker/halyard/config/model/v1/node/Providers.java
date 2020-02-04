/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.model.v1.node;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.netflix.spinnaker.halyard.config.model.v1.providers.appengine.AppengineProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.aws.AwsProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.azure.AzureProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.cloudfoundry.CloudFoundryProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dcos.DCOSProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dockerRegistry.DockerRegistryProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.ecs.EcsProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.huaweicloud.HuaweiCloudProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.oracle.OracleBMCSProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.oracle.OracleProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.tencentcloud.TencentCloudProvider;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties({"openstack"})
public class Providers extends Node implements Cloneable {
  AppengineProvider appengine = new AppengineProvider();
  AwsProvider aws = new AwsProvider();
  EcsProvider ecs = new EcsProvider();
  AzureProvider azure = new AzureProvider();
  DCOSProvider dcos = new DCOSProvider();
  DockerRegistryProvider dockerRegistry = new DockerRegistryProvider();
  GoogleProvider google = new GoogleProvider();
  HuaweiCloudProvider huaweicloud = new HuaweiCloudProvider();
  KubernetesProvider kubernetes = new KubernetesProvider();
  TencentCloudProvider tencentcloud = new TencentCloudProvider();

  @JsonProperty(access = Access.WRITE_ONLY)
  OracleBMCSProvider oraclebmcs = new OracleBMCSProvider();

  OracleProvider oracle = new OracleProvider();
  CloudFoundryProvider cloudfoundry = new CloudFoundryProvider();

  @Override
  public String getNodeName() {
    return "provider";
  }

  public OracleProvider getOracle() {
    return OracleProvider.mergeOracleBMCSProvider(oracle, oraclebmcs);
  }

  @Override
  public NodeIterator getChildren() {
    List<Node> nodes = new ArrayList<Node>();

    NodeIterator children = NodeIteratorFactory.makeReflectiveIterator(this);
    Node child = children.getNext();
    while (child != null) {
      if (!child.getNodeName().equals("oracle") && !child.getNodeName().equals("oraclebmcs")) {
        nodes.add(child);
      }
      child = children.getNext();
    }

    nodes.add(OracleProvider.mergeOracleBMCSProvider(oracle, oraclebmcs));

    return NodeIteratorFactory.makeListIterator(nodes);
  }

  public static Class<? extends Provider> translateProviderType(String providerName) {
    Optional<? extends Class<?>> res =
        Arrays.stream(Providers.class.getDeclaredFields())
            .filter(f -> f.getName().equals(providerName))
            .map(Field::getType)
            .findFirst();

    if (res.isPresent()) {
      return (Class<? extends Provider>) res.get();
    } else {
      throw new IllegalArgumentException(
          "No provider with name \"" + providerName + "\" handled by halyard");
    }
  }

  public static Class<? extends Account> translateAccountType(String providerName) {
    Class<? extends Provider> providerClass = translateProviderType(providerName);

    String accountClassName = providerClass.getName().replaceAll("Provider", "Account");
    try {
      return (Class<? extends Account>) Class.forName(accountClassName);
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException(
          "No account for class \"" + accountClassName + "\" found", e);
    }
  }

  public static Class<? extends BakeryDefaults> translateBakeryDefaultsType(String providerName) {
    Class<? extends Provider> providerClass = translateProviderType(providerName);

    String bakeryDefaultsClassName =
        providerClass.getName().replaceAll("Provider", "BakeryDefaults");
    try {
      return (Class<? extends BakeryDefaults>) Class.forName(bakeryDefaultsClassName);
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException(
          "No bakeryDefaults for class \"" + bakeryDefaultsClassName + "\" found", e);
    }
  }

  public static Class<? extends BaseImage> translateBaseImageType(String providerName) {
    Class<? extends Provider> providerClass = translateProviderType(providerName);

    String baseImageClassName = providerClass.getName().replaceAll("Provider", "BaseImage");
    try {
      return (Class<? extends BaseImage>) Class.forName(baseImageClassName);
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException(
          "No baseImage for class \"" + baseImageClassName + "\" found", e);
    }
  }

  public static Class<? extends Cluster> translateClusterType(String providerName) {
    Class<? extends Provider> providerClass = translateProviderType(providerName);

    String clusterClassName = providerClass.getName().replaceAll("Provider", "Cluster");
    try {
      return (Class<? extends Cluster>) Class.forName(clusterClassName);
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException(
          "No cluster for class \"" + clusterClassName + "\" found", e);
    }
  }
}
