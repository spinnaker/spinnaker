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

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

public class PrometheusConfigurationProperties {

  /**
   * TODO(duftler): Once we've finished docs for all target platforms, move this somewhere more appropriate.
   * GCE:
   * This default assumes you've configured Prometheus service discovery to automatically identify
   * the GCE instances to scrape. The following sample configuration also does the required relabelling
   * such that the instance names are used instead of the ip addresses.

   - job_name: 'gce_svc_disco'

     gce_sd_configs:
     - project: $PROJECT-ID
       zone: $ZONE
       refresh_interval: 60s
       port: 9100

     relabel_configs:
     - source_labels: [__meta_gce_instance_name]
       target_label: instance
       replacement: $1
     - source_labels: [__meta_gce_zone]
       target_label: zone
       replacement: $1

   */
  @Getter
  @Setter
  private String scopeLabel = "instance";

  @Getter
  private List<PrometheusManagedAccount> accounts = new ArrayList<>();
}
