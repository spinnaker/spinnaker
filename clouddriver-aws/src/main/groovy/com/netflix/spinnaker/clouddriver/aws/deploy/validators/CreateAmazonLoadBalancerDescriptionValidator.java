/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.validators;

import com.amazonaws.services.elasticloadbalancingv2.model.AuthenticateOidcActionConfig;
import com.netflix.spinnaker.clouddriver.aws.AmazonOperation;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerClassicDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertAmazonLoadBalancerV2Description;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@AmazonOperation(AtomicOperations.UPSERT_LOAD_BALANCER)
@Component("createAmazonLoadBalancerDescriptionValidator")
class CreateAmazonLoadBalancerDescriptionValidator
    extends AmazonDescriptionValidationSupport<UpsertAmazonLoadBalancerDescription> {
  private void validateActions(
      List<UpsertAmazonLoadBalancerV2Description.Action> actions,
      Set<String> allTargetGroupNames,
      Set<String> unusedTargetGroupNames,
      Errors errors) {
    for (UpsertAmazonLoadBalancerV2Description.Action action : actions) {
      if (action.getType().equals("forward")) {
        String targetGroupName = action.getTargetGroupName();
        if (!allTargetGroupNames.contains(targetGroupName)) {
          errors.rejectValue(
              "listeners", "createAmazonLoadBalancerDescription.listeners.invalid.targetGroup");
        }
        unusedTargetGroupNames.remove(action.getTargetGroupName());
      }

      if (action.getType().equals("authenticate-oidc")) {
        AuthenticateOidcActionConfig config = action.getAuthenticateOidcActionConfig();
        if (config.getClientId() == null) {
          errors.rejectValue(
              "listeners", "createAmazonLoadBalancerDescription.listeners.invalid.oidcConfig");
        }
      }
    }
  }

  @Override
  public void validate(
      List priorDescriptions, UpsertAmazonLoadBalancerDescription description, Errors errors) {
    // Common fields to validate
    if (description.getName() == null && description.getClusterName() == null) {
      errors.rejectValue(
          "clusterName", "createAmazonLoadBalancerDescription.missing.name.or.clusterName");
    }
    if (description.getSubnetType() == null && description.getAvailabilityZones() == null) {
      errors.rejectValue(
          "availabilityZones",
          "createAmazonLoadBalancerDescription.missing.subnetType.or.availabilityZones");
    }

    if (description.getAvailabilityZones() != null) {
      for (Map.Entry<String, List<String>> entry : description.getAvailabilityZones().entrySet()) {
        String region = entry.getKey();
        List<String> azs = entry.getValue();

        AmazonCredentials.AWSRegion acctRegion =
            description.getCredentials().getRegions().stream()
                .filter(r -> r.getName().equals(region))
                .findFirst()
                .orElse(null);
        if (acctRegion == null) {
          errors.rejectValue(
              "availabilityZones", "createAmazonLoadBalancerDescription.region.not.configured");
        }
        if (description.getSubnetType() == null && azs == null) {
          errors.rejectValue(
              "availabilityZones",
              "createAmazonLoadBalancerDescription.missing.subnetType.or.availabilityZones");
          break;
        }
        if (description.getSubnetType() == null
            && acctRegion != null
            && !acctRegion.getAvailabilityZones().containsAll(azs)) {
          errors.rejectValue(
              "availabilityZones", "createAmazonLoadBalancerDescription.zone.not.configured");
        }
      }
    }

    switch (description.getLoadBalancerType()) {
      case CLASSIC:
        UpsertAmazonLoadBalancerClassicDescription classicDescription =
            (UpsertAmazonLoadBalancerClassicDescription) description;
        if (classicDescription.getListeners() == null
            || classicDescription.getListeners().size() == 0) {
          errors.rejectValue("listeners", "createAmazonLoadBalancerDescription.listeners.empty");
        }

        if (classicDescription.getDeregistrationDelay() != null) {
          if (classicDescription.getDeregistrationDelay() < 1
              || classicDescription.getDeregistrationDelay() > 3600) {
            errors.rejectValue(
                "deregistrationDelay",
                "createAmazonLoadBalancerDescription.deregistrationDelay.invalid");
          }
        }
        break;
      case APPLICATION:
      case NETWORK:
        UpsertAmazonLoadBalancerV2Description albDescription =
            (UpsertAmazonLoadBalancerV2Description) description;
        if (albDescription.targetGroups == null || albDescription.targetGroups.size() == 0) {
          errors.rejectValue(
              "targetGroups", "createAmazonLoadBalancerDescription.targetGroups.empty");
        }

        Set<String> allTargetGroupNames = new HashSet<>();
        for (UpsertAmazonLoadBalancerV2Description.TargetGroup targetGroup :
            albDescription.targetGroups) {
          allTargetGroupNames.add(targetGroup.getName());
          if (targetGroup.getName() == null || targetGroup.getName().isEmpty()) {
            errors.rejectValue(
                "targetGroups", "createAmazonLoadBalancerDescription.targetGroups.name.missing");
          }
          if (targetGroup.getProtocol() == null) {
            errors.rejectValue(
                "targetGroups",
                "createAmazonLoadBalancerDescription.targetGroups.protocol.missing");
          }
          if (targetGroup.getPort() == null) {
            errors.rejectValue(
                "targetGroups", "createAmazonLoadBalancerDescription.targetGroups.port.missing");
          }
        }
        Set<String> unusedTargetGroupNames = new HashSet<>();
        unusedTargetGroupNames.addAll(allTargetGroupNames);

        for (UpsertAmazonLoadBalancerV2Description.Listener listener : albDescription.listeners) {
          if (listener.getDefaultActions().size() == 0) {
            errors.rejectValue(
                "listeners", "createAmazonLoadBalancerDescription.listeners.missing.defaultAction");
          }
          this.validateActions(
              listener.getDefaultActions(), allTargetGroupNames, unusedTargetGroupNames, errors);
          for (UpsertAmazonLoadBalancerV2Description.Rule rule : listener.getRules()) {
            this.validateActions(
                rule.getActions(), allTargetGroupNames, unusedTargetGroupNames, errors);
          }
        }
        if (unusedTargetGroupNames.size() > 0) {
          errors.rejectValue(
              "targetGroups", "createAmazonLoadBalancerDescription.targetGroups.unused");
        }
        break;
      default:
        errors.rejectValue(
            "loadBalancerType", "createAmazonLoadBalancerDescription.loadBalancerType.invalid");
        break;
    }
  }
}
