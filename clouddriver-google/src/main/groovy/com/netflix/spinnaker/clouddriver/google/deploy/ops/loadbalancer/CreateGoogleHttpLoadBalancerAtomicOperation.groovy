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

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.compute.model.*
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.description.CreateGoogleHttpLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationException
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleHostRule
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleHttpLoadBalancingPolicy
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class CreateGoogleHttpLoadBalancerAtomicOperation implements AtomicOperation<Map> {
  private static final String BASE_PHASE = "CREATE_HTTP_LOAD_BALANCER"
  private static final String HEALTH_CHECK_NAME_PREFIX = "hc"
  private static final String BACKEND_SERVICE_NAME_PREFIX = "bs"
  private static final String PATH_MATCHER_PREFIX = "path-matcher"
  private static final String TARGET_HTTP_PROXY_NAME_PREFIX = "target-http-proxy"
  private static final String TARGET_HTTPS_PROXY_NAME_PREFIX = "target-https-proxy"

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
   * minimal command:
   * curl -v -X POST -H "Content-Type: application/json" -d '[{ "createGoogleHttpLoadBalancerDescription": {"credentials": "my-google-account", "googleHttpLoadBalancer": {"name": "http-create", "portRange": "80", "defaultService": {"name": "default-backend-service", "backends": [], "healthCheck": {"name": "basic-check", "requestPath": "/", "port": 80, "checkIntervalSec": 1, "timeoutSec": 1, "healthyThreshold": 1, "unhealthyThreshold": 1}}, "certificate": "", "hostRules": [] }}}]' localhost:7002/ops
   *
   * full command:
   * curl -v -X POST -H "Content-Type: application/json" -d '[{ "createGoogleHttpLoadBalancerDescription": {"credentials": "my-google-account", "googleHttpLoadBalancer": {"name": "http-create", "portRange": "80", "defaultService": {"name": "default-backend-service", "backends": [], "healthCheck": {"name": "basic-check", "requestPath": "/", "port": 80, "checkIntervalSec": 1, "timeoutSec": 1, "healthyThreshold": 1, "unhealthyThreshold": 1}}, "certificate": "", "hostRules": [{"hostPatterns": ["host1.com", "host2.com"], "pathMatcher": {"pathRules": [{"paths": ["/path", "/path2/more"], "backendService": {"name": "backend-service", "backends": [], "healthCheck": {"name": "health-check", "requestPath": "/", "port": 80, "checkIntervalSec": 1, "timeoutSec": 1, "healthyThreshold": 1, "unhealthyThreshold": 1}}}], "defaultService": {"name": "pm-backend-service", "backends": [], "healthCheck": {"name": "derp-check", "requestPath": "/", "port": 80, "checkIntervalSec": 1, "timeoutSec": 1, "healthyThreshold": 1, "unhealthyThreshold": 1}}}}]}}}]' localhost:7002/ops
   *
   * @param description
   * @param priorOutputs
   * @return
   */
  @Override
  Map operate(List priorOutputs) {
    def httpLoadBalancer = description.googleHttpLoadBalancer
    def httpLoadBalancerName = httpLoadBalancer.name

    task.updateStatus BASE_PHASE, "Initializing creation of HTTP load balancer $httpLoadBalancerName..."

    if (!description.credentials) {
      throw new IllegalArgumentException("Unable to resolve credentials for Google account '${description.accountName}'.")
    }

    def compute = description.credentials.compute
    def project = description.credentials.project

    List<GoogleBackendService> backendServicesFromDescription = Utils.getBackendServicesFromHttpLoadBalancerView(httpLoadBalancer.view).unique()
    List<GoogleHealthCheck> healthChecksFromDescription = backendServicesFromDescription.collect { it.healthCheck }.unique()

    List<HttpHealthCheck> existingHealthChecks = compute.httpHealthChecks().list(project).execute().getItems()
    healthChecksFromDescription.each { GoogleHealthCheck healthCheck ->
      String healthCheckName = "$httpLoadBalancerName-$HEALTH_CHECK_NAME_PREFIX-$healthCheck.name"

      def existingHealthCheck = existingHealthChecks.findAll { it.name == healthCheckName }
      if (existingHealthCheck) {
        throw new GoogleOperationException("There is already a health check named ${healthCheck.name} " +
            "associated with $httpLoadBalancerName. Please specify a different name.")
      }

      task.updateStatus BASE_PHASE, "Creating health check $healthCheckName..."
      HttpHealthCheck newHealthCheck = new HttpHealthCheck(
          name: healthCheckName,
          port: healthCheck.port,
          requestPath: healthCheck.requestPath,
          checkIntervalSec: healthCheck.checkIntervalSec,
          healthyThreshold: healthCheck.healthyThreshold,
          unhealthyThreshold: healthCheck.unhealthyThreshold,
          timeoutSec: healthCheck.timeoutSec,
      )
      def insertHealthCheckOperation = compute.httpHealthChecks().insert(project, newHealthCheck).execute()
      googleOperationPoller.waitForGlobalOperation(compute, project, insertHealthCheckOperation.getName(),
          null, task, "health check " + healthCheckName, BASE_PHASE)
    }

    List<BackendService> existingServices = compute.backendServices().list(project).execute().getItems()
    backendServicesFromDescription.each { GoogleBackendService backendService ->
      String backendServiceName = "$httpLoadBalancerName-$BACKEND_SERVICE_NAME_PREFIX-$backendService.name"

      def existingService = existingServices.findAll { it.name == backendServiceName }
      if (existingService) {
        throw new GoogleOperationException("There is already a backend service named ${backendService.name} " +
            "associated with $httpLoadBalancerName. Please specify a different name.")
      }

      task.updateStatus BASE_PHASE, "Creating backend service $backendServiceName..."
      BackendService bs = new BackendService(
          name: backendServiceName,
          portName: GoogleHttpLoadBalancingPolicy.HTTP_PORT_NAME,
          healthChecks: [GCEUtil.buildHttpHealthCheckUrl(project, "$httpLoadBalancerName-$HEALTH_CHECK_NAME_PREFIX-${backendService.healthCheck.name}")]
      )
      def insertBackendServiceOperation = compute.backendServices().insert(project, bs).execute()
      googleOperationPoller.waitForGlobalOperation(compute, project, insertBackendServiceOperation.getName(),
          null, task, "backend service " + backendServiceName, BASE_PHASE)
    }

    String urlMapName = httpLoadBalancerName // An L7 load balancer is identified by it's UrlMap name in Pantheon.
    UrlMap existingUrlMap = null
    try {
      existingUrlMap = compute.urlMaps().get(project, urlMapName).execute()
    } catch (GoogleJsonResponseException e) {
      // 404 is thrown if the url map doesn't exist. Any other exception needs to be propagated.
      if (e.getStatusCode() != 404) {
        throw e
      }
    }

    if (existingUrlMap) {
      throw new GoogleOperationException("There is already a load balancer named " +
          "$httpLoadBalancerName. Please specify a different name.")
    }
    task.updateStatus BASE_PHASE, "Creating URL map $urlMapName..."
    UrlMap newUrlMap = new UrlMap(name: urlMapName, hostRules: [], pathMatchers: [])
    newUrlMap.defaultService = serviceUrl(project, httpLoadBalancerName, httpLoadBalancer.defaultService.name)
    httpLoadBalancer?.hostRules?.each { GoogleHostRule hostRule ->
      String pathMatcherName = "$httpLoadBalancerName-$PATH_MATCHER_PREFIX-${System.currentTimeMillis()}"
      def pathMatcher = hostRule.pathMatcher
      PathMatcher newPathMatcher = new PathMatcher(
          name: pathMatcherName,
          defaultService: serviceUrl(project, httpLoadBalancerName, pathMatcher.defaultService.name),
          pathRules: pathMatcher.pathRules.collect {
            new PathRule(
                paths: it.paths,
                service: serviceUrl(project, httpLoadBalancerName, it.backendService.name)
            )
          }
      )
      newUrlMap.pathMatchers << newPathMatcher
      newUrlMap.hostRules << new HostRule(pathMatcher: pathMatcherName, hosts: hostRule.hostPatterns)
    }
    def insertUrlMapOperation = compute.urlMaps().insert(project, newUrlMap).execute()
    googleOperationPoller.waitForGlobalOperation(compute, project, insertUrlMapOperation.getName(),
        null, task, "url map " + urlMapName, BASE_PHASE)
    def urlMapUrl = insertUrlMapOperation.getTargetLink()

    String targetProxyName
    def targetProxy
    def insertTargetProxyOperation
    if (httpLoadBalancer.certificate) {
      targetProxyName = "$httpLoadBalancerName-$TARGET_HTTPS_PROXY_NAME_PREFIX"
      task.updateStatus BASE_PHASE, "Creating target proxy $targetProxyName..."
      targetProxy = new TargetHttpsProxy(
          name: targetProxyName,
          sslCertificates: [GCEUtil.buildCertificateUrl(project, httpLoadBalancer.certificate)],
          urlMap: urlMapUrl,
      )
      insertTargetProxyOperation = compute.targetHttpsProxies().insert(project, targetProxy).execute()
    } else {
      targetProxyName = "$httpLoadBalancerName-$TARGET_HTTP_PROXY_NAME_PREFIX"
      task.updateStatus BASE_PHASE, "Creating target proxy $targetProxyName..."
      targetProxy = new TargetHttpProxy(name: targetProxyName, urlMap: urlMapUrl)
      insertTargetProxyOperation = compute.targetHttpProxies().insert(project, targetProxy).execute()
    }

    googleOperationPoller.waitForGlobalOperation(compute, project, insertTargetProxyOperation.getName(),
        null, task, "target proxy " + targetProxyName, BASE_PHASE)
    String targetProxyUrl = insertTargetProxyOperation.getTargetLink()

    task.updateStatus BASE_PHASE, "Creating global forwarding rule $httpLoadBalancerName..."
    def forwardingRule = new ForwardingRule(
        name: httpLoadBalancerName,
        iPAddress: httpLoadBalancer.ipAddress,
        iPProtocol: httpLoadBalancer.ipProtocol,
        portRange: httpLoadBalancer.certificate ? "443" : httpLoadBalancer.portRange,
        target: targetProxyUrl,
    )
    compute.globalForwardingRules().insert(project, forwardingRule).execute()

    task.updateStatus BASE_PHASE, "Done creating HTTP load balancer $httpLoadBalancerName"
    [loadBalancers: [("global"): [name: httpLoadBalancerName]]]
  }

  private static String serviceUrl(String project, String loadBalancerName, String serviceName) {
    GCEUtil.buildBackendServiceUrl(project, "$loadBalancerName-$BACKEND_SERVICE_NAME_PREFIX-$serviceName")
  }
}
