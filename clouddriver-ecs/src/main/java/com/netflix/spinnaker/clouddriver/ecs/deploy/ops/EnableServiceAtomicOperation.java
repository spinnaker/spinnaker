/*
 * Copyright 2018 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.deploy.ops;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.applicationautoscaling.AWSApplicationAutoScaling;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalableTargetsRequest;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalableTargetsResult;
import com.amazonaws.services.applicationautoscaling.model.ScalableDimension;
import com.amazonaws.services.applicationautoscaling.model.ScalableTarget;
import com.amazonaws.services.applicationautoscaling.model.ServiceNamespace;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.UpdateServiceRequest;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.ModifyServiceDescription;
import java.util.ArrayList;
import java.util.List;

public class EnableServiceAtomicOperation
    extends AbstractEcsAtomicOperation<ModifyServiceDescription, Void> {

  public EnableServiceAtomicOperation(ModifyServiceDescription description) {
    super(description, "ENABLE_ECS_SERVER_GROUP");
  }

  @Override
  public Void operate(List priorOutputs) {
    updateTaskStatus("Initializing Enable Amazon ECS Server Group Operation...");
    enableService();
    return null;
  }

  private void enableService() {
    AmazonECS ecsClient = getAmazonEcsClient();

    String service = description.getServerGroupName();
    String account = description.getCredentialAccount();
    String cluster = getCluster(service, account);

    UpdateServiceRequest request =
        new UpdateServiceRequest()
            .withCluster(cluster)
            .withService(service)
            .withDesiredCount(getMaxCapacity(cluster));

    updateTaskStatus(String.format("Enabling %s server group for %s.", service, account));
    ecsClient.updateService(request);
    updateTaskStatus(String.format("Server group %s enabled for %s.", service, account));
  }

  private Integer getMaxCapacity(String cluster) {
    ScalableTarget target = getScalableTarget(cluster);
    if (target != null) {
      return target.getMaxCapacity();
    }
    return 1;
  }

  private ScalableTarget getScalableTarget(String cluster) {
    AWSApplicationAutoScaling appASClient = getAmazonApplicationAutoScalingClient();

    List<String> resourceIds = new ArrayList<>();
    resourceIds.add(String.format("service/%s/%s", cluster, description.getServerGroupName()));

    DescribeScalableTargetsRequest request =
        new DescribeScalableTargetsRequest()
            .withResourceIds(resourceIds)
            .withScalableDimension(ScalableDimension.EcsServiceDesiredCount)
            .withServiceNamespace(ServiceNamespace.Ecs);

    DescribeScalableTargetsResult result = appASClient.describeScalableTargets(request);

    if (result.getScalableTargets().isEmpty()) {
      return null;
    }

    if (result.getScalableTargets().size() == 1) {
      return result.getScalableTargets().get(0);
    }

    throw new Error("Multiple Scalable Targets found");
  }

  private AWSApplicationAutoScaling getAmazonApplicationAutoScalingClient() {
    AWSCredentialsProvider credentialsProvider = getCredentials().getCredentialsProvider();
    String region = description.getRegion();
    NetflixAmazonCredentials credentialAccount = description.getCredentials();

    return amazonClientProvider.getAmazonApplicationAutoScaling(credentialAccount, region, false);
  }
}
