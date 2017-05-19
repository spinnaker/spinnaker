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

import com.netflix.spinnaker.halyard.config.model.v1.providers.appengine.AppengineProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.aws.AwsProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.azure.AzureProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dcos.DCOSProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.dockerRegistry.DockerRegistryProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.google.GoogleProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.kubernetes.KubernetesProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.openstack.OpenstackProvider;
import com.netflix.spinnaker.halyard.config.model.v1.providers.oraclebmcs.OracleBMCSProvider;
import com.netflix.spinnaker.halyard.config.problem.v1.ConfigProblemSetBuilder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;

@Data
@EqualsAndHashCode(callSuper = false)
public class Providers extends Node implements Cloneable {
  AppengineProvider appengine = new AppengineProvider();
  AwsProvider aws = new AwsProvider();
  AzureProvider azure = new AzureProvider();
  DCOSProvider dcos = new DCOSProvider();
  DockerRegistryProvider dockerRegistry = new DockerRegistryProvider();
  GoogleProvider google = new GoogleProvider();
  KubernetesProvider kubernetes = new KubernetesProvider();
  OpenstackProvider openstack = new OpenstackProvider();
  OracleBMCSProvider oraclebmcs = new OracleBMCSProvider();

  @Override
  public String getNodeName() {
    return "provider";
  }

  @Override
  public NodeIterator getChildren() {
    return NodeIteratorFactory.makeReflectiveIterator(this);
  }

  @Override
  public void accept(ConfigProblemSetBuilder psBuilder, Validator v) {
    v.validate(psBuilder, this);
  }

  public static Class<? extends Provider> translateProviderType(String providerName) {
    Optional<? extends Class<?>> res = Arrays.stream(Providers.class.getDeclaredFields())
        .filter(f -> f.getName().equals(providerName))
        .map(Field::getType)
        .findFirst();

    if (res.isPresent()) {
      return (Class<? extends Provider>)res.get();
    } else {
      throw new IllegalArgumentException("No provider with name \"" + providerName + "\" handled by halyard");
    }
  }

  public static Class<? extends Account> translateAccountType(String providerName) {
    Class<? extends Provider> providerClass = translateProviderType(providerName);

    String accountClassName = providerClass.getName().replaceAll("Provider", "Account");
    try {
      return (Class<? extends Account>) Class.forName(accountClassName);
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException("No account for class \"" + accountClassName + "\" found", e);
    }
  }

  public static Class<? extends BakeryDefaults> translateBakeryDefaultsType(String providerName) {
    Class<? extends Provider> providerClass = translateProviderType(providerName);

    String bakeryDefaultsClassName = providerClass.getName().replaceAll("Provider", "BakeryDefaults");
    try {
      return (Class<? extends BakeryDefaults>) Class.forName(bakeryDefaultsClassName);
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException("No bakeryDefaults for class \"" + bakeryDefaultsClassName + "\" found", e);
    }
  }

  public static Class<? extends BaseImage> translateBaseImageType(String providerName) {
    Class<? extends Provider> providerClass = translateProviderType(providerName);

    String baseImageClassName = providerClass.getName().replaceAll("Provider", "BaseImage");
    try {
      return (Class<? extends BaseImage>) Class.forName(baseImageClassName);
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException("No baseImage for class \"" + baseImageClassName + "\" found", e);
    }
  }

  public static Class<? extends Cluster> translateClusterType(String providerName) {
    Class<? extends Provider> providerClass = translateProviderType(providerName);

    String clusterClassName = providerClass.getName().replaceAll("Provider", "Cluster");
    try {
      return (Class<? extends Cluster>) Class.forName(clusterClassName);
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException("No cluster for class \"" + clusterClassName + "\" found", e);
    }
  }

}
