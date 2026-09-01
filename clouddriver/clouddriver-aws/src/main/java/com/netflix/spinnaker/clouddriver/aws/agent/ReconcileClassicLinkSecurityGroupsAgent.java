/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.agent;

import com.google.common.base.Strings;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.cats.agent.AccountAware;
import com.netflix.spinnaker.cats.agent.RunnableAgent;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsCleanupProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent;
import com.netflix.spinnaker.config.AwsConfiguration;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.AttachClassicLinkVpcRequest;
import software.amazon.awssdk.services.ec2.model.ClassicLinkInstance;
import software.amazon.awssdk.services.ec2.model.DescribeClassicLinkInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeClassicLinkInstancesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsRequest;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.GroupIdentifier;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.SecurityGroup;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.VpcClassicLink;

public class ReconcileClassicLinkSecurityGroupsAgent
    implements RunnableAgent, CustomScheduledAgent, AccountAware {

  static final String AUTOSCALING_TAG = "aws:autoscaling:groupName";
  static final int RUNNING_STATE = 16;

  private final Logger log = LoggerFactory.getLogger(getClass());
  public static final long DEFAULT_POLL_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(30);
  public static final long DEFAULT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(5);
  public static final long DEFAULT_REQUIRED_INSTANCE_LIFETIME = TimeUnit.MINUTES.toMillis(5);

  private final AmazonClientProvider amazonClientProvider;
  private final NetflixAmazonCredentials account;
  private final String region;
  private final AwsConfiguration.DeployDefaults deployDefaults;
  private final long pollIntervalMillis;
  private final long timeoutMillis;
  private final long requiredInstanceLifetime;
  private final Clock clock;

  @Override
  public String getAccountName() {
    return account.getName();
  }

  public ReconcileClassicLinkSecurityGroupsAgent(
      AmazonClientProvider amazonClientProvider,
      NetflixAmazonCredentials account,
      String region,
      AwsConfiguration.DeployDefaults deployDefaults) {
    this(
        amazonClientProvider,
        account,
        region,
        deployDefaults,
        DEFAULT_POLL_INTERVAL_MILLIS,
        DEFAULT_TIMEOUT_MILLIS,
        DEFAULT_REQUIRED_INSTANCE_LIFETIME,
        Clock.systemUTC());
  }

  public ReconcileClassicLinkSecurityGroupsAgent(
      AmazonClientProvider amazonClientProvider,
      NetflixAmazonCredentials account,
      String region,
      AwsConfiguration.DeployDefaults deployDefaults,
      long pollIntervalMillis,
      long timeoutMillis,
      long requiredInstanceLifetime,
      Clock clock) {
    this.amazonClientProvider = amazonClientProvider;
    this.account = account;
    this.region = region;
    this.deployDefaults = deployDefaults;
    this.pollIntervalMillis = pollIntervalMillis;
    this.timeoutMillis = timeoutMillis;
    this.requiredInstanceLifetime = requiredInstanceLifetime;
    this.clock = clock;
  }

  @Override
  public void run() {
    if (!deployDefaults.isReconcileClassicLinkAccount(account)) {
      return;
    }
    log.info("Checking classic link security groups in {}/{}", account.getName(), region);
    Ec2Client ec2 = amazonClientProvider.getAmazonEC2V2(account, region);
    List<String> classicLinkVpcIds =
        ec2.describeVpcClassicLink().vpcs().stream()
            .filter(VpcClassicLink::classicLinkEnabled)
            .map(VpcClassicLink::vpcId)
            .collect(Collectors.toList());
    if (classicLinkVpcIds.size() > 1) {
      log.warn("Multiple classicLinkVpcs found: {}", classicLinkVpcIds);
      throw new IllegalStateException("More than 1 classicLinkVpc found: " + classicLinkVpcIds);
    }

    if (classicLinkVpcIds.isEmpty()) {
      return;
    }
    String classicLinkVpcId = classicLinkVpcIds.get(0);

    final Map<String, ClassicLinkInstance> classicLinkInstances = new HashMap<>();
    DescribeInstancesRequest describeInstances =
        DescribeInstancesRequest.builder().maxResults(500).build();
    while (true) {
      DescribeInstancesResponse instanceResult = ec2.describeInstances(describeInstances);
      instanceResult.reservations().stream()
          .flatMap(r -> r.instances().stream())
          .filter(i -> i.vpcId() == null)
          .filter(
              i ->
                  Optional.ofNullable(i.state())
                      .filter(is -> is.code() == RUNNING_STATE)
                      .isPresent())
          .filter(this::isInstanceOldEnough)
          .map(
              i ->
                  ClassicLinkInstance.builder()
                      .instanceId(i.instanceId())
                      .vpcId(classicLinkVpcId)
                      .tags(i.tags())
                      .build())
          .forEach(cli -> classicLinkInstances.put(cli.instanceId(), cli));

      if (instanceResult.nextToken() == null) {
        break;
      }
      describeInstances =
          describeInstances.toBuilder().nextToken(instanceResult.nextToken()).build();
    }

    DescribeClassicLinkInstancesRequest request =
        DescribeClassicLinkInstancesRequest.builder().maxResults(1000).build();
    while (true) {
      DescribeClassicLinkInstancesResponse result = ec2.describeClassicLinkInstances(request);
      result.instances().forEach(i -> classicLinkInstances.put(i.instanceId(), i));
      if (result.nextToken() == null) {
        break;
      }
      request = request.toBuilder().nextToken(result.nextToken()).build();
    }

    log.info(
        "{} existing classic instances in {}/{}",
        classicLinkInstances.size(),
        account.getName(),
        region);

    Map<String, String> groupNamesToIds =
        ec2
            .describeSecurityGroups(
                DescribeSecurityGroupsRequest.builder()
                    .filters(Filter.builder().name("vpc-id").values(classicLinkVpcId).build())
                    .build())
            .securityGroups()
            .stream()
            .collect(Collectors.toMap(SecurityGroup::groupName, SecurityGroup::groupId));

    reconcileInstances(ec2, groupNamesToIds, classicLinkInstances.values());
  }

  boolean isInstanceOldEnough(Instance instance) {
    return Optional.ofNullable(instance.launchTime())
        .map(i -> i.plusMillis(requiredInstanceLifetime))
        .map(i -> clock.instant().isAfter(i))
        .orElse(false);
  }

  void reconcileInstances(
      Ec2Client ec2,
      Map<String, String> groupNamesToIds,
      Collection<ClassicLinkInstance> instances) {
    StringBuilder report = new StringBuilder();
    for (ClassicLinkInstance i : instances) {
      List<String> existingClassicLinkGroups =
          i.groups().stream().map(GroupIdentifier::groupId).collect(Collectors.toList());

      int maxNewGroups =
          deployDefaults.getMaxClassicLinkSecurityGroups() - existingClassicLinkGroups.size();
      if (maxNewGroups > 0) {
        String asgName =
            i.tags().stream()
                .filter(t -> AUTOSCALING_TAG.equals(t.key()))
                .map(Tag::value)
                .findFirst()
                .orElse(null);

        List<String> candidateGroupNames = getSecurityGroupNames(asgName);

        List<String> missingGroupIds =
            candidateGroupNames.stream()
                .map(groupNamesToIds::get)
                .filter(name -> name != null && !existingClassicLinkGroups.contains(name))
                .limit(maxNewGroups)
                .collect(Collectors.toList());

        if (!missingGroupIds.isEmpty()) {
          List<String> groupIds = new ArrayList<>(existingClassicLinkGroups);
          groupIds.addAll(missingGroupIds);
          if (deployDefaults.getReconcileClassicLinkSecurityGroups()
              == AwsConfiguration.DeployDefaults.ReconcileMode.MODIFY) {
            try {
              ec2.attachClassicLinkVpc(
                  AttachClassicLinkVpcRequest.builder()
                      .vpcId(i.vpcId())
                      .groups(groupIds)
                      .instanceId(i.instanceId())
                      .build());
            } catch (AwsServiceException ase) {
              log.warn("Failed calling attachClassicLinkVpc", ase);
            }
          }
          report
              .append("\n\t")
              .append(Strings.padStart(i.instanceId(), 24, ' '))
              .append(missingGroupIds);
        }
      }
    }
    if (report.length() > 0) {
      log.info(
          "Attach to classicLinkVpc: account: "
              + account.getName()
              + ", region: "
              + region
              + report);
    }
  }

  private List<String> getSecurityGroupNames(String asgName) {
    Set<String> groups = new LinkedHashSet<>();
    Optional.ofNullable(deployDefaults.getClassicLinkSecurityGroupName()).ifPresent(groups::add);
    if (deployDefaults.isAddAppGroupsToClassicLink()) {
      Optional.ofNullable(asgName)
          .map(Names::parseName)
          .ifPresent(
              names ->
                  Optional.ofNullable(names.getApp())
                      .ifPresent(
                          appGroup -> {
                            groups.add(appGroup);
                            Optional<String> stackGroup =
                                Optional.ofNullable(names.getStack())
                                    .map(stack -> appGroup + "-" + stack);
                            stackGroup.ifPresent(groups::add);
                            Optional<String> detailGroup =
                                Optional.ofNullable(names.getDetail())
                                    .map(
                                        detail -> stackGroup.orElse(appGroup + "-") + "-" + detail);
                            detailGroup.ifPresent(groups::add);
                          }));
    }
    return groups.stream().collect(Collectors.toList());
  }

  @Override
  public long getPollIntervalMillis() {
    return pollIntervalMillis;
  }

  @Override
  public long getTimeoutMillis() {
    return timeoutMillis;
  }

  @Override
  public String getAgentType() {
    return account.getName() + "/" + region + "/" + getClass().getSimpleName();
  }

  @Override
  public String getProviderName() {
    return AwsCleanupProvider.PROVIDER_NAME;
  }
}
