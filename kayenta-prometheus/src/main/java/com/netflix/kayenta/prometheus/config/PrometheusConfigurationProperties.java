/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.kayenta.prometheus.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

public class PrometheusConfigurationProperties {

  @Getter @Setter private long metadataCachingIntervalMS = Duration.ofSeconds(60).toMillis();

  /**
   * TODO(duftler): Once we've finished docs for all target platforms, move this somewhere more
   * appropriate. GCE: Configures Prometheus service discovery to automatically identify the GCE
   * instances to scrape. The following sample configuration also does the required relabelling such
   * that the instance names are used instead of the ip addresses.
   *
   * <p>- job_name: 'gce_svc_discovery'
   *
   * <p>gce_sd_configs: - project: $PROJECT-ID zone: $ZONE refresh_interval: 60s port: 9100
   *
   * <p>relabel_configs: - source_labels: [__meta_gce_instance_name] target_label: instance
   * replacement: $1 - source_labels: [__meta_gce_zone] target_label: zone replacement: $1
   *
   * <p>AWS/EC2: Configures Prometheus service discovery to automatically identify the EC2 instances
   * to scrape. The following sample configuration also does the required relabelling such that the
   * asg names are retrieved from the instance tags.
   *
   * <p>- job_name: 'aws_svc_discovery'
   *
   * <p>ec2_sd_configs: - region: $REGION access_key: *** secret_key: *** port: 9100
   *
   * <p>relabel_configs: - source_labels: [__meta_ec2_tag_aws_autoscaling_groupName] target_label:
   * asg_groupName - source_labels: [__meta_ec2_availability_zone] target_label: zone replacement:
   * $1
   */
  @Getter @Setter private String scopeLabel = "instance";

  @Getter private List<PrometheusManagedAccount> accounts = new ArrayList<>();
}
