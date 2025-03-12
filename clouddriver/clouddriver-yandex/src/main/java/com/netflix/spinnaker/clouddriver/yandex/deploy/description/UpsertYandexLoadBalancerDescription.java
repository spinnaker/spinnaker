/*
 * Copyright 2020 YANDEX LLC
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

package com.netflix.spinnaker.clouddriver.yandex.deploy.description;

import com.google.common.collect.ImmutableList;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.security.resources.ApplicationNameable;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudLoadBalancer;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class UpsertYandexLoadBalancerDescription
    implements CredentialsChangeable, ApplicationNameable {
  private YandexCloudCredentials credentials;

  private String id;
  private String name;
  private String description;
  private YandexCloudLoadBalancer.BalancerType lbType;
  private List<YandexCloudLoadBalancer.Listener> listeners;
  private Map<String, String> labels;

  @Override
  public Collection<String> getApplications() {
    return ImmutableList.of(Names.parseName(name).getApp());
  }
}
