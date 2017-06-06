/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.description;

import com.amazonaws.services.elasticloadbalancingv2.model.ActionTypeEnum;
import com.amazonaws.services.elasticloadbalancingv2.model.Certificate;
import com.amazonaws.services.elasticloadbalancingv2.model.ProtocolEnum;

import java.util.List;

public class UpsertAmazonLoadBalancerV2Description extends UpsertAmazonLoadBalancerDescription {
  public List<Listener> listeners;
  public List<TargetGroup> targetGroups;

  public static class TargetGroup {
    private String name;
    private ProtocolEnum protocol;
    private Integer port;
    private Attributes attributes; // TODO: Support target group attributes

    private ProtocolEnum healthCheckProtocol;
    private String healthCheckPath;
    private String healthCheckPort;
    private Integer healthCheckInterval = 10;
    private Integer healthCheckTimeout = 5;
    private Integer unhealthyThreshold = 2;
    private Integer healthyThreshold = 10;
    private String healthCheckMatcher = "200-299"; // string of ranges or individual http status codes, separated by commas


    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public ProtocolEnum getProtocol() {
      return protocol;
    }

    public void setProtocol(ProtocolEnum protocol) {
      this.protocol = protocol;
    }

    public Integer getPort() {
      return port;
    }

    public void setPort(Integer port) {
      this.port = port;
    }

    public Attributes getAttributes() {
      return attributes;
    }

    public void setAttributes(Attributes attributes) {
      this.attributes = attributes;
    }

    public ProtocolEnum getHealthCheckProtocol() {
      return healthCheckProtocol;
    }

    public void setHealthCheckProtocol(ProtocolEnum healthCheckProtocol) {
      this.healthCheckProtocol = healthCheckProtocol;
    }

    public String getHealthCheckPath() {
      return healthCheckPath;
    }

    public void setHealthCheckPath(String healthCheckPath) {
      this.healthCheckPath = healthCheckPath;
    }

    public String getHealthCheckPort() {
      return healthCheckPort;
    }

    public void setHealthCheckPort(String healthCheckPort) {
      this.healthCheckPort = healthCheckPort;
    }

    public Integer getHealthCheckInterval() {
      return healthCheckInterval;
    }

    public void setHealthCheckInterval(Integer healthCheckInterval) {
      this.healthCheckInterval = healthCheckInterval;
    }

    public Integer getHealthCheckTimeout() {
      return healthCheckTimeout;
    }

    public void setHealthCheckTimeout(Integer healthCheckTimeout) {
      this.healthCheckTimeout = healthCheckTimeout;
    }

    public Integer getUnhealthyThreshold() {
      return unhealthyThreshold;
    }

    public void setUnhealthyThreshold(Integer unhealthyThreshold) {
      this.unhealthyThreshold = unhealthyThreshold;
    }

    public Integer getHealthyThreshold() {
      return healthyThreshold;
    }

    public void setHealthyThreshold(Integer healthyThreshold) {
      this.healthyThreshold = healthyThreshold;
    }

    public String getHealthCheckMatcher() {
      return healthCheckMatcher;
    }

    public void setHealthCheckMatcher(String healthCheckMatcher) {
      this.healthCheckMatcher = healthCheckMatcher;
    }

    public Boolean compare(com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup awsTargetGroup) {
      return this.name.equals(awsTargetGroup.getTargetGroupName()) &&
        this.protocol.toString().equals(awsTargetGroup.getProtocol()) &&
        this.port.equals(awsTargetGroup.getPort()) &&
        this.healthCheckProtocol.toString().equals(awsTargetGroup.getHealthCheckProtocol()) &&
        this.healthCheckPath.equals(awsTargetGroup.getHealthCheckPath()) &&
        this.healthCheckPort.equals(awsTargetGroup.getHealthCheckPort()) &&
        this.healthCheckInterval.equals(awsTargetGroup.getHealthCheckIntervalSeconds()) &&
        this.healthCheckTimeout.equals(awsTargetGroup.getHealthCheckTimeoutSeconds()) &&
        this.healthyThreshold.equals(awsTargetGroup.getHealthyThresholdCount()) &&
        this.unhealthyThreshold.equals(awsTargetGroup.getUnhealthyThresholdCount()) &&
        this.healthCheckMatcher.equals(awsTargetGroup.getMatcher().getHttpCode());

    }
  }

  public static class Listener {
    private List<Certificate> certificates;
    private ProtocolEnum protocol;
    private Integer port;
    private String sslPolicy;
    private List<Action> defaultActions;

    public List<Certificate> getCertificates() {
      return certificates;
    }

    public void setCertificates(List<Certificate> certificates) {
      this.certificates = certificates;
    }

    public ProtocolEnum getProtocol() {
      return protocol;
    }

    public void setProtocol(ProtocolEnum protocol) {
      this.protocol = protocol;
    }

    public Integer getPort() {
      return port;
    }

    public void setPort(Integer port) {
      this.port = port;
    }

    public String getSslPolicy() {
      return sslPolicy;
    }

    public void setSslPolicy(String sslPolicy) {
      this.sslPolicy = sslPolicy;
    }

    public List<Action> getDefaultActions() {
      return defaultActions;
    }

    public void setDefaultActions(List<Action> defaultActions) {
      this.defaultActions = defaultActions;
    }

    public Boolean compare(com.amazonaws.services.elasticloadbalancingv2.model.Listener awsListener, List<com.amazonaws.services.elasticloadbalancingv2.model.Action> actions) {
      Boolean actionsSame = awsListener.getDefaultActions().containsAll(actions) &&
        actions.containsAll(awsListener.getDefaultActions());
      Boolean sslPolicySame = (this.sslPolicy == null && awsListener.getSslPolicy() == null) ||
        (this.sslPolicy != null && this.sslPolicy.equals(awsListener.getSslPolicy()));

      return (this.protocol != null && this.protocol.toString().equals(awsListener.getProtocol())) &&
        (this.port != null && this.port.equals(awsListener.getPort())) &&
        actionsSame &&
        sslPolicySame;
    }
  }

  public static class Action {
    private ActionTypeEnum type = ActionTypeEnum.Forward;
    private String targetGroupName;

    public ActionTypeEnum getType() {
      return type;
    }

    public void setType(ActionTypeEnum type) {
      this.type = type;
    }

    public String getTargetGroupName() {
      return targetGroupName;
    }

    public void setTargetGroupName(String targetGroupName) {
      this.targetGroupName = targetGroupName;
    }
  }

  public static class Attributes {
    private Integer deregistrationDelay = 300;
    private Boolean stickinessEnabled = false;
    private String stickinessType = "lb_cookie";
    private Integer stickinessDuration = 86400;

    public Integer getDeregistrationDelay() {
      return deregistrationDelay;
    }

    public void setDeregistrationDelay(Integer deregistrationDelay) {
      this.deregistrationDelay = deregistrationDelay;
    }

    public Boolean getStickinessEnabled() {
      return stickinessEnabled;
    }

    public void setStickinessEnabled(Boolean stickinessEnabled) {
      this.stickinessEnabled = stickinessEnabled;
    }

    public String getStickinessType() {
      return stickinessType;
    }

    public void setStickinessType(String stickinessType) {
      this.stickinessType = stickinessType;
    }

    public Integer getStickinessDuration() {
      return stickinessDuration;
    }

    public void setStickinessDuration(Integer stickinessDuration) {
      this.stickinessDuration = stickinessDuration;
    }
  }
}
