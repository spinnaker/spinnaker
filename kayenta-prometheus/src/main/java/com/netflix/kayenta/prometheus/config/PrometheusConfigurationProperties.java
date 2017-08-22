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

import com.netflix.kayenta.retrofit.config.RemoteService;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

// TODO(ewiseblatt):
// @Data this class instead of @Getter/Setter and final the "accounts".
public class PrometheusConfigurationProperties {

  /**
   * TODO(ewiseblatt):
   * We're saying "instance" here by default, which is built-in to Prometheus.
   * However, in practice this should be overriden to "host" where "host"
   * is injected on scrape by the prometheus.yml configuration. The install
   * for prometheus when using the --gce option does this, but that is the
   * only configuration that does at this time.
   *
   * The difference is that prometheus adds "instance" as the __address__,
   * which is the <host>:<port> but the <host> is typically an IP address.
   * Instance can be explicitly overriden as well, but should be the particular
   * service endpoint.
   *
   * In general, you do want the particular service endpoint in order to get
   * the particular service of interest. In the case of looking for node_cpu,
   * which is the default, then you would need to add the node_exporter service
   * to the request (i.e. instance is <host>:9100). Using the above "host", this
   * would collect *all* node_cpu from that host. However we assume that only
   * the node_exporter is exporting "node_cpu" so there is only one (port 9100).
   *
   * I need to clean this up. Perhaps by configuring default prometheus to use
   * the dns name instead of the IP in general for the __address__. Regardless,
   * the application scraping can determine its own "instance" value (as well
   * as "host") in which case the operator might need to compensate in how they
   * configure this scopeLabel value and query using it.
   */
  @Getter
  @Setter
  private String scopeLabel = "instance";

  // Location of prometheus server.
  @NotNull
  @Getter
  @Setter
  private RemoteService endpoint;

  @Getter
  private List<PrometheusManagedAccount> accounts = new ArrayList<>();
}
