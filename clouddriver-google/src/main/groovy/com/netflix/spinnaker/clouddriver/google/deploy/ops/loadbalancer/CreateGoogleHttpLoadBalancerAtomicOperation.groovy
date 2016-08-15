/*
 * Copyright 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.google.deploy.ops.loadbalancer

import com.google.api.services.compute.model.Backend
import com.google.api.services.compute.model.BackendService
import com.google.api.services.compute.model.ForwardingRule
import com.google.api.services.compute.model.HostRule
import com.google.api.services.compute.model.PathMatcher
import com.google.api.services.compute.model.PathRule
import com.google.api.services.compute.model.TargetHttpProxy
import com.google.api.services.compute.model.UrlMap
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.description.CreateGoogleHttpLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class CreateGoogleHttpLoadBalancerAtomicOperation  implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "CREATE_HTTP_LB"
  private static final String HEALTH_CHECK_NAME_PREFIX = "health-check"
  private static final String BACKEND_SERVICE_NAME_PREFIX = "backend-service"
  private static final String URL_MAP_NAME_PREFIX = "url-map"
  private static final String TARGET_HTTP_PROXY_NAME_PREFIX = "target-http-proxy"
  private static final String BACKEND_PORT_NAME = "http"
  private static final String IP_PROTOCOL = "HTTP"
  private static final String DEFAULT_PORT_RANGE = "80"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  private GoogleOperationPoller googleOperationPoller

  private final CreateGoogleHttpLoadBalancerDescription description

  CreateGoogleHttpLoadBalancerAtomicOperation(CreateGoogleHttpLoadBalancerDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[{ "createGoogleHttpLoadBalancerDescription": {"credentials": "my-account-name", "loadBalancerName": "http-lb", "googleHttpLoadBalancer": {"name": "http-lb", "defaultService": {"name": "default-backend-service", "backends": [], "healthCheck": {"name": "basic-check", "requestPath": "/", "port": 80}}, "certificate": "cert-name", "hostRules": [{"hostPatterns": ["host1.com", "host2.com"], "pathMatcher": {"pathRules": [{"paths": ["/path", "/path2/more"], "backendService": {"name": "backend-service", "backends": [], "healthCheck": {"name": "basic-check", "requestPath": "/", "port": 80}}}] "defaultService": {"name": "pm-backend-service", "backends": [], "healthCheck": {"name": "basic-check", "requestPath": "/", "port": 80}}}}]}}}]' localhost:7002/ops
   *
   * @param description
   * @param priorOutputs
   * @return
   */
  @Override
  DeploymentResult operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing creation of HTTP load balancer $description.loadBalancerName..."

    if (!description.credentials) {
      throw new IllegalArgumentException("Unable to resolve credentials for Google account '${description.accountName}'.")
    }

    def compute = description.credentials.compute
    def project = description.credentials.project

    // Make health check.
    def healthCheckName = String.format("%s-%s-%d", description.loadBalancerName, HEALTH_CHECK_NAME_PREFIX, System.currentTimeMillis())

    task.updateStatus BASE_PHASE, "Creating health check $healthCheckName..."

    def httpHealthChecksResourceLinks = new ArrayList<String>();
    def httpHealthCheck = GCEUtil.makeHttpHealthCheck(healthCheckName, description.healthCheck)
    def createHttpHealthCheckOperation = compute.httpHealthChecks().insert(project, httpHealthCheck).execute()
    def httpHealthCheckUrl = createHttpHealthCheckOperation.getTargetLink()
    httpHealthChecksResourceLinks.add(httpHealthCheckUrl)

    // Make backend service.
    def backends = new ArrayList<Backend>();
    for (backend in description.backends) {
      backends.add(new Backend(
          group: backend.group,
          balancingMode: backend.balancingMode,
          maxUtilization: backend.maxUtilization,
          capacityScaler: backend.capacityScaler
      ))
    }

    def backendServiceName = String.format("%s-%s-%d", description.loadBalancerName, BACKEND_SERVICE_NAME_PREFIX, System.currentTimeMillis())

    task.updateStatus BASE_PHASE, "Creating backend service $backendServiceName..."

    def backendService = new BackendService(
        name: backendServiceName,
        backends: backends,
        timeoutSec: description.backendTimeoutSec,
        healthChecks: httpHealthChecksResourceLinks,
        port: description.backendPort,
        portName: BACKEND_PORT_NAME,
        protocol: IP_PROTOCOL,
    )

    // Before building the backend service we must check and wait until the health check is built.
    googleOperationPoller.waitForGlobalOperation(compute, project, createHttpHealthCheckOperation.getName(),
        null, task, "http health check " + GCEUtil.getLocalName(httpHealthCheckUrl), BASE_PHASE)

    def backendServiceOperation = compute.backendServices().insert(project, backendService).execute()
    def backendServiceUrl = backendServiceOperation.getTargetLink()

    // Make URL map.
    def pathMatchers = new ArrayList<PathMatcher>();
    for (pathMatcher in description.pathMatchers) {
      def pathRules = new ArrayList<PathRule>();
      for (pathRule in pathMatcher.pathRules) {
        pathRules.add(
            paths: pathRule.paths,
            service: pathRule.service
        )
      }
      pathMatchers.add(
          name: pathMatcher.name,
          defaultService: pathMatcher.defaultService,
          pathRules: pathRules
      )
    }

    def hostRules = new ArrayList<HostRule>();
    for (hostRule in description.hostRules) {
      hostRules.add(
          hosts: hostRule.hosts,
          pathMatcher: hostRule.pathMatcher
      )
    }

    def urlMapName = String.format("%s-%s-%d", description.loadBalancerName, URL_MAP_NAME_PREFIX, System.currentTimeMillis())

    task.updateStatus BASE_PHASE, "Creating URL map $urlMapName..."

    def urlMap = new UrlMap(
        name: urlMapName,
        pathMatchers: pathMatchers,
        hostRules: hostRules,
        defaultService: backendServiceUrl
    )

    // Before building the url map we must check and wait until the backend service is built.
    googleOperationPoller.waitForGlobalOperation(compute, project, backendServiceOperation.getName(),
        null, task, "backend service " + GCEUtil.getLocalName(backendServiceUrl), BASE_PHASE)

    def urlMapOperation = compute.urlMaps().insert(project, urlMap).execute()
    def urlMapUrl = urlMapOperation.getTargetLink()

    // Make target HTTP proxy.
    def targetHttpProxyName = String.format("%s-%s-%d", description.loadBalancerName, TARGET_HTTP_PROXY_NAME_PREFIX, System.currentTimeMillis())

    task.updateStatus BASE_PHASE, "Creating target HTTP proxy $targetHttpProxyName..."

    def targetHttpProxy = new TargetHttpProxy(
        name: targetHttpProxyName,
        urlMap: urlMapUrl
    )

    // Before building the target http proxy we must check and wait until the url map is built.
    googleOperationPoller.waitForGlobalOperation(compute, project, urlMapOperation.getName(),
        null, task, "url map " + GCEUtil.getLocalName(urlMapUrl), BASE_PHASE)

    def targetHttpProxyOperation = compute.targetHttpProxies().insert(project, targetHttpProxy).execute()
    def targetHttpProxyUrl = targetHttpProxyOperation.getTargetLink()

    // Finally, make forwarding rule.
    task.updateStatus BASE_PHASE, "Creating forwarding rule $description.loadBalancerName..."

    def portRange
    if (!description.portRange) {
      portRange = DEFAULT_PORT_RANGE
    } else {
      portRange = description.portRange
    }

    def forwardingRule = new ForwardingRule(
        name: description.loadBalancerName,
        iPAddress: description.ipAddress,
        portRange: portRange,
        target: targetHttpProxyUrl
    )

    // Before building the forwarding rule we must check and wait until the target http proxy is built.
    googleOperationPoller.waitForGlobalOperation(compute, project, targetHttpProxyOperation.getName(),
        null, task, "target http proxy " + GCEUtil.getLocalName(targetHttpProxyUrl), BASE_PHASE)

    compute.globalForwardingRules().insert(project, forwardingRule).execute()

    task.updateStatus BASE_PHASE, "Done creating HTTP load balancer $description.loadBalancerName."
    null
  }
}
