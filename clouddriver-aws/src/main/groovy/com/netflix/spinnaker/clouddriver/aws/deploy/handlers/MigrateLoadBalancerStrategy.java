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

package com.netflix.spinnaker.clouddriver.aws.deploy.handlers;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancing.model.*;
import com.netflix.spinnaker.config.AwsConfiguration.DeployDefaults;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertSecurityGroupDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.LoadBalancerMigrator;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.LoadBalancerMigrator.LoadBalancerLocation;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.LoadBalancerMigrator.TargetLoadBalancerLocation;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.MigrateLoadBalancerResult;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.MigrateSecurityGroupReference;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.MigrateSecurityGroupResult;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupLookup;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupMigrator;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupMigrator.SecurityGroupLocation;
import com.netflix.spinnaker.clouddriver.aws.model.SubnetTarget;
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonVpcProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class MigrateLoadBalancerStrategy implements MigrateStrategySupport {

  protected SecurityGroupLookup sourceLookup;
  protected SecurityGroupLookup targetLookup;
  protected MigrateSecurityGroupStrategy migrateSecurityGroupStrategy;

  protected LoadBalancerLocation source;
  protected TargetLoadBalancerLocation target;
  protected String subnetType;
  protected String applicationName;
  protected boolean allowIngressFromClassic;
  protected boolean dryRun;

  abstract AmazonClientProvider getAmazonClientProvider();

  abstract RegionScopedProviderFactory getRegionScopedProviderFactory();

  abstract DeployDefaults getDeployDefaults();

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  /**
   * Generates a result set describing the actions required to migrate the source load balancer to the target.
   *
   * @param sourceLookup            a security group lookup cache for the source region
   * @param targetLookup            a security group lookup cache for the target region (may be the same object as the sourceLookup)
   * @param source                  the source load balancer
   * @param target                  the target location
   * @param subnetType              the subnetType in which to migrate the load balancer (should be null for EC Classic migrations)
   * @param applicationName         the name of the source application
   * @param allowIngressFromClassic whether ingress should be granted from classic link
   * @param dryRun                  whether to actually perform the migration
   * @return the result set
   */
  public synchronized MigrateLoadBalancerResult generateResults(SecurityGroupLookup sourceLookup, SecurityGroupLookup targetLookup,
                                                                MigrateSecurityGroupStrategy migrateSecurityGroupStrategy,
                                                                LoadBalancerLocation source, TargetLoadBalancerLocation target,
                                                                String subnetType, String applicationName,
                                                                boolean allowIngressFromClassic, boolean dryRun) {

    this.migrateSecurityGroupStrategy = migrateSecurityGroupStrategy;
    this.sourceLookup = sourceLookup;
    this.targetLookup = targetLookup;
    this.source = source;
    this.target = target;
    this.subnetType = subnetType;
    this.applicationName = applicationName;
    this.allowIngressFromClassic = allowIngressFromClassic;
    this.dryRun = dryRun;

    if (!target.isUseZonesFromSource() && (target.getAvailabilityZones() == null || target.getAvailabilityZones().isEmpty())) {
      throw new IllegalStateException("No availability zones specified for load balancer migration");
    }

    final MigrateLoadBalancerResult result = new MigrateLoadBalancerResult();

    LoadBalancerDescription sourceLoadBalancer = getLoadBalancer(source.getCredentials(), source.getRegion(), source.getName());
    if (sourceLoadBalancer == null) {
      throw new IllegalStateException("Source load balancer not found: " + source);
    }

    if (target.isUseZonesFromSource()) {
      target.setAvailabilityZones(sourceLoadBalancer.getAvailabilityZones());
      if (target.getAvailabilityZones() == null || target.getAvailabilityZones().isEmpty()) {
        throw new IllegalStateException("No availability zones specified for load balancer migration");
      }
    }

    Vpc sourceVpc = getVpc(source);
    Vpc targetVpc = getVpc(target);

    String targetName = target.getName() != null ? target.getName() : generateLoadBalancerName(source.getName(), sourceVpc, targetVpc);
    LoadBalancerDescription targetLoadBalancer = getLoadBalancer(target.getCredentials(), target.getRegion(), targetName);
    verifyLoadBalancerName(result, targetName, targetLoadBalancer);

    List<MigrateSecurityGroupResult> targetSecurityGroups = getTargetSecurityGroups(sourceLoadBalancer, result);

    List<String> securityGroups = targetSecurityGroups.stream()
      .filter(g -> !g.getSkipped().contains(g.getTarget()))
      .map(g -> g.getTarget().getTargetId()).distinct().collect(Collectors.toList());
    securityGroups.addAll(buildExtraSecurityGroups(sourceLoadBalancer, result));

    result.getSecurityGroups().addAll(targetSecurityGroups);

    result.setTargetName(targetName);
    result.setTargetExists(targetLoadBalancer != null);
    if (!dryRun) {
      updateTargetLoadBalancer(sourceLoadBalancer, targetLoadBalancer, targetName, securityGroups, result);
    }

    return result;
  }

  /*
    If migrating from Classic to VPC and the name cannot be updated, require a new name
    If this is not a dry run, we'll throw an exception to halt the migration
   */
  private void verifyLoadBalancerName(MigrateLoadBalancerResult result, String targetName, LoadBalancerDescription targetLoadBalancer) {
    boolean invalid = target.getVpcId() != null && targetLoadBalancer != null && targetLoadBalancer.getVPCId() == null;
    if (targetName.equals(source.getName()) && target.getCredentialAccount().equals(source.getCredentialAccount()) && target.getRegion().equals(source.getRegion())) {
      invalid = true;
    }
    if (invalid) {
      if (dryRun) {
        result.setNewNameRequired(true);
      } else {
        throw new IllegalStateException("A load balancer named '" + targetName + "' already exists in EC2 Classic and cannot be reused when migrating to VPC");
      }
    }
  }

  /**
   * Performs the actual upsert operation against the target load balancer
   *
   * @param sourceLoadBalancer the Amazon load balancer description of the source load balancer
   * @param targetLoadBalancer the Amazon load balancer description of the target load balancer (may be null)
   * @param targetName         the name of the target load balancer
   * @param securityGroups     a list of security group names to attach to the load balancer
   */
  protected void updateTargetLoadBalancer(LoadBalancerDescription sourceLoadBalancer,
                                          LoadBalancerDescription targetLoadBalancer,
                                          String targetName, Collection<String> securityGroups,
                                          MigrateLoadBalancerResult result) {

    List<Listener> listeners = getListeners(sourceLoadBalancer, result);

    List<String> subnetIds = subnetType != null ?
      getRegionScopedProviderFactory().forRegion(target.getCredentials(), target.getRegion())
        .getSubnetAnalyzer().getSubnetIdsForZones(target.getAvailabilityZones(), subnetType, SubnetTarget.ELB, 1) :
      new ArrayList();
    if (subnetIds != null && subnetIds.isEmpty() && subnetType != null) {
      throw new IllegalStateException("Cannot " + targetLoadBalancer == null ? "create" : "update" + targetName + ".  No subnets found for subnet type: " + subnetType);
    }
    AmazonElasticLoadBalancing sourceClient = getAmazonClientProvider()
      .getAmazonElasticLoadBalancing(source.getCredentials(), source.getRegion(), true);
    AmazonElasticLoadBalancing targetClient = getAmazonClientProvider()
      .getAmazonElasticLoadBalancing(target.getCredentials(), target.getRegion(), true);
    if (targetLoadBalancer == null) {
      boolean isInternal = subnetType == null || subnetType.contains("internal");
      LoadBalancerAttributes sourceAttributes = getLoadBalancerAttributes(sourceLoadBalancer, sourceClient);
      LoadBalancerUpsertHandler.createLoadBalancer(
        targetClient, targetName, isInternal, target.getAvailabilityZones(), subnetIds, listeners, securityGroups, sourceAttributes);
      configureHealthCheck(targetClient, sourceLoadBalancer, targetName);
    } else {
      LoadBalancerUpsertHandler.updateLoadBalancer(targetClient, targetLoadBalancer, listeners, securityGroups);
    }
    applyListenerPolicies(sourceClient, targetClient, sourceLoadBalancer, targetName);
  }

  public List<Listener> getListeners(LoadBalancerDescription sourceLoadBalancer, MigrateLoadBalancerResult result) {
    List<Listener> unmigratableListeners = sourceLoadBalancer.getListenerDescriptions().stream()
      .map(ListenerDescription::getListener)
      .filter(listenerCannotBeMigrated(source, target)).collect(Collectors.toList());

    unmigratableListeners.forEach(l -> result.getWarnings().add(
      "The following listeners could not be created: " +
        l.getProtocol() + ":" + l.getLoadBalancerPort() + " => " +
        l.getInstanceProtocol() + ":" + l.getInstancePort() + " (certificate: " + l.getSSLCertificateId() + ")."
    ));

    List<Listener> listeners = sourceLoadBalancer.getListenerDescriptions().stream()
      .map(ListenerDescription::getListener)
      .filter(l -> l.getInstancePort() > 0)  // strip out invalid load balancer listeners from legacy ELBs
      .collect(Collectors.toList());

    listeners.removeAll(unmigratableListeners);
    return listeners;
  }

  public LoadBalancerAttributes getLoadBalancerAttributes(LoadBalancerDescription sourceLoadBalancer, AmazonElasticLoadBalancing sourceClient) {
    LoadBalancerAttributes sourceAttributes = sourceClient.describeLoadBalancerAttributes(
      new DescribeLoadBalancerAttributesRequest().withLoadBalancerName(sourceLoadBalancer.getLoadBalancerName())).getLoadBalancerAttributes();
    if (sourceLoadBalancer.getListenerDescriptions().stream().anyMatch(l -> l.getListener().getInstancePort() == 0)) {
      sourceAttributes.setCrossZoneLoadBalancing(new CrossZoneLoadBalancing().withEnabled(true));
    }
    return sourceAttributes;
  }


  /**
   * Applies any listener policies from the source load balancer to the target load balancer.
   *
   * Since policy names are unique to each load balancer, two policies with the same name in different load balancers
   * may contain different policy attributes. For the sake of simplicity, we assume that policies with the same name
   * are structurally the same, and do not attempt to reconcile any differences between attributes.
   *
   * We will, however, attempt to override the policies applied to a given listener if it's different, e.g., if the
   * source load balancer has policy "a" on port 7000, and the target load balancer has policy "b" on port 7000, we
   * will:
   *   1. create policy "a" if it doesn't exist on the target load balancer, then
   *   2. update the target load balancer so port 7000 will have only policy "a"
   */
  public void applyListenerPolicies(AmazonElasticLoadBalancing sourceClient, AmazonElasticLoadBalancing targetClient,
                                     LoadBalancerDescription source, String loadBalancerName) {
    Set<String> policiesToRetrieve = new HashSet<>();
    Map<String, String> policyNameMap = new HashMap<>();
    source.getListenerDescriptions().forEach(d -> policiesToRetrieve.addAll(d.getPolicyNames()));
    List<PolicyDescription> sourcePolicies = sourceClient.describeLoadBalancerPolicies(
      new DescribeLoadBalancerPoliciesRequest()
        .withLoadBalancerName(source.getLoadBalancerName())
        .withPolicyNames(policiesToRetrieve)).getPolicyDescriptions();
    List<PolicyDescription> targetPolicies = targetClient.describeLoadBalancerPolicies(
      new DescribeLoadBalancerPoliciesRequest()
        .withLoadBalancerName(loadBalancerName)
    ).getPolicyDescriptions();

    sourcePolicies.forEach(p -> {
      Optional<PolicyDescription> match = targetPolicies.stream().filter(tp ->
        tp.getPolicyAttributeDescriptions().size() == p.getPolicyAttributeDescriptions().size()
          && tp.getPolicyAttributeDescriptions().containsAll(p.getPolicyAttributeDescriptions()))
        .findFirst();

      if (match.isPresent()) {
        policyNameMap.put(p.getPolicyName(), match.get().getPolicyName());
      } else {
        String policyName = p.getPolicyName();
        if (policyName.startsWith("ELBSample-") || policyName.startsWith("ELBSecurityPolicy-")) {
          policyName = "migrated-" + policyName;
        }
        policyNameMap.put(p.getPolicyName(), policyName);
        CreateLoadBalancerPolicyRequest request = new CreateLoadBalancerPolicyRequest()
          .withPolicyName(policyName)
          .withLoadBalancerName(loadBalancerName)
          .withPolicyTypeName(p.getPolicyTypeName());
        // only copy policy attributes if this is not a pre-defined policy
        // (as defined by the presence of 'Reference-Security-Policy'
        Optional<PolicyAttributeDescription> referencePolicy = p.getPolicyAttributeDescriptions().stream()
          .filter(d -> d.getAttributeName().equals("Reference-Security-Policy")).findFirst();
        if (referencePolicy.isPresent()) {
          request.withPolicyAttributes(
            new PolicyAttribute(referencePolicy.get().getAttributeName(), referencePolicy.get().getAttributeValue()));
        } else {
          request.withPolicyAttributes(p.getPolicyAttributeDescriptions().stream().map(d ->
            new PolicyAttribute(d.getAttributeName(), d.getAttributeValue())).collect(Collectors.toList()));
        }
        targetClient.createLoadBalancerPolicy(request);
      }
    });
    source.getListenerDescriptions().forEach(l -> targetClient.setLoadBalancerPoliciesOfListener(
      new SetLoadBalancerPoliciesOfListenerRequest()
        .withLoadBalancerName(loadBalancerName)
      .withLoadBalancerPort(l.getListener().getLoadBalancerPort())
      .withPolicyNames(l.getPolicyNames().stream().map(policyNameMap::get).collect(Collectors.toList()))
      )
    );
  }


  private void configureHealthCheck(AmazonElasticLoadBalancing loadBalancing,
                                    LoadBalancerDescription source, String loadBalancerName) {
    HealthCheck healthCheck = new HealthCheck()
      .withTarget(source.getHealthCheck().getTarget())
      .withInterval(source.getHealthCheck().getInterval())
      .withTimeout(source.getHealthCheck().getTimeout())
      .withUnhealthyThreshold(source.getHealthCheck().getUnhealthyThreshold())
      .withHealthyThreshold(source.getHealthCheck().getHealthyThreshold());

    loadBalancing.configureHealthCheck(new ConfigureHealthCheckRequest(loadBalancerName, healthCheck));
  }

  private Predicate<Listener> listenerCannotBeMigrated(LoadBalancerLocation source, LoadBalancerLocation target) {
    return l -> l.getSSLCertificateId() != null && !source.getCredentialAccount().equals(target.getCredentialAccount());
  }

  private LoadBalancerDescription getLoadBalancer(NetflixAmazonCredentials credentials, String region, String name) {
    try {
      AmazonElasticLoadBalancing client = getAmazonClientProvider()
        .getAmazonElasticLoadBalancing(credentials, region, true);
      DescribeLoadBalancersResult targetLookup = client.describeLoadBalancers(
        new DescribeLoadBalancersRequest().withLoadBalancerNames(name));
      return targetLookup.getLoadBalancerDescriptions().get(0);
    } catch (Exception ignored) {
      return null;
    }
  }

  /**
   * Generates a list of security groups to add to the load balancer in addition to those on the source load balancer
   *
   * @param sourceDescription       the AWS description of the source load balancer
   * @param result                  the result set for the load balancer migration - this will potentially be mutated as a side effect
   * @return a list security group ids that should be added to the load balancer
   */
  protected List<String> buildExtraSecurityGroups(LoadBalancerDescription sourceDescription,
                                                  MigrateLoadBalancerResult result) {
    ArrayList<String> newGroups = new ArrayList<>();
    if (target.getVpcId() != null) {
      AmazonEC2 targetAmazonEC2 = getAmazonClientProvider().getAmazonEC2(target.getCredentials(), target.getRegion(), true);
      List<SecurityGroup> appGroups = new ArrayList<>();
      try {
        List<String> groupNames = Arrays.asList(applicationName, applicationName + "-elb");
        appGroups = targetAmazonEC2.describeSecurityGroups(new DescribeSecurityGroupsRequest().withFilters(
          new Filter("group-name", groupNames))).getSecurityGroups();
      } catch (Exception ignored) {
      }

      String elbGroupId = buildElbSecurityGroup(sourceDescription, appGroups, result);
      newGroups.add(elbGroupId);
    }
    return newGroups;
  }

  /**
   * Creates an elb specific security group, or returns the ID of one if it already exists
   *
   * @param sourceDescription       the AWS description of the source load balancer
   * @param appGroups               list of existing security groups in which to look for existing elb security group
   * @param result                  the result set for the load balancer migration - this will potentially be mutated as a side effect
   * @return the groupId of the elb security group
   */
  protected String buildElbSecurityGroup(LoadBalancerDescription sourceDescription, List<SecurityGroup> appGroups,
                                         MigrateLoadBalancerResult result) {
    String elbGroupId = null;
    Optional<SecurityGroup> existingGroup = appGroups.stream()
      .filter(g -> g.getVpcId() != null && g.getVpcId().equals(target.getVpcId()) && g.getGroupName().equals(applicationName + "-elb"))
      .findFirst();
    if (existingGroup.isPresent()) {
      if (!dryRun && allowIngressFromClassic) {
        addClassicLinkIngress(targetLookup, getDeployDefaults().getClassicLinkSecurityGroupName(),
          existingGroup.get().getGroupId(), target.getCredentials(), target.getVpcId());
      }
      return existingGroup.get().getGroupId();
    }
    MigrateSecurityGroupReference elbGroup = new MigrateSecurityGroupReference();
    elbGroup.setAccountId(target.getCredentials().getAccountId());
    elbGroup.setVpcId(target.getVpcId());
    elbGroup.setTargetName(applicationName + "-elb");
    MigrateSecurityGroupResult addedGroup = new MigrateSecurityGroupResult();
    addedGroup.setTarget(elbGroup);
    addedGroup.getCreated().add(elbGroup);
    result.getSecurityGroups().add(addedGroup);
    if (!dryRun) {
      UpsertSecurityGroupDescription upsertDescription = new UpsertSecurityGroupDescription();
      upsertDescription.setDescription("Application load balancer security group for " + applicationName);
      upsertDescription.setName(applicationName + "-elb");
      upsertDescription.setVpcId(target.getVpcId());
      upsertDescription.setRegion(target.getRegion());
      upsertDescription.setCredentials(target.getCredentials());
      getTask().updateStatus(LoadBalancerMigrator.BASE_PHASE, "Creating load balancer security group " +
        upsertDescription.getName() + " in " + target.getCredentialAccount() + "/" + target.getRegion() + "/" + target.getVpcId());
      elbGroupId = targetLookup.createSecurityGroup(upsertDescription).getSecurityGroup().getGroupId();
      AmazonEC2 targetAmazonEC2 = getAmazonClientProvider().getAmazonEC2(target.getCredentials(), target.getRegion(), true);
      elbGroup.setTargetId(elbGroupId);
      if (source.getVpcId() == null && allowIngressFromClassic) {
        addClassicLinkIngress(targetLookup, getDeployDefaults().getClassicLinkSecurityGroupName(),
          elbGroupId, target.getCredentials(), target.getVpcId());
        addPublicIngress(targetAmazonEC2, elbGroupId, sourceDescription);
      }
    }
    if (getDeployDefaults().getAddAppGroupToServerGroup()) {
      buildApplicationSecurityGroup(sourceDescription, appGroups, addedGroup);
    }

    return elbGroupId;
  }

  /**
   * Creates the app specific security group, or returns the ID of one if it already exists
   *
   * @param appGroups               list of existing security groups in which to look for existing app security group
   * @param elbGroup                the elb specific security group, which will allow ingress permission from the
   *                                app specific security group
   */
  protected void buildApplicationSecurityGroup(LoadBalancerDescription sourceDescription, List<SecurityGroup> appGroups,
                                               MigrateSecurityGroupResult elbGroup) {
    if (getDeployDefaults().getAddAppGroupToServerGroup()) {
      AmazonEC2 targetAmazonEC2 = getAmazonClientProvider().getAmazonEC2(target.getCredentials(), target.getRegion(), true);
      Optional<SecurityGroup> existing = appGroups.stream().filter(isAppSecurityGroup()).findFirst();
      MigrateSecurityGroupReference appGroupReference = new MigrateSecurityGroupReference();
      appGroupReference.setAccountId(target.getCredentials().getAccountId());
      appGroupReference.setVpcId(target.getVpcId());
      appGroupReference.setTargetName(applicationName);
      if (existing.isPresent()) {
        elbGroup.getReused().add(appGroupReference);
      } else {
        elbGroup.getCreated().add(appGroupReference);
        if (!dryRun) {
          UpsertSecurityGroupDescription upsertDescription = new UpsertSecurityGroupDescription();
          upsertDescription.setDescription("Application security group for " + applicationName);
          upsertDescription.setName(applicationName);
          upsertDescription.setVpcId(target.getVpcId());
          upsertDescription.setRegion(target.getRegion());
          upsertDescription.setCredentials(target.getCredentials());
          getTask().updateStatus(LoadBalancerMigrator.BASE_PHASE, "Creating security group " +
            upsertDescription.getName() + " in " + target.getCredentialAccount() + "/" + target.getRegion() + "/" + target.getVpcId());
          String newGroupId = targetLookup.createSecurityGroup(upsertDescription).getSecurityGroup().getGroupId();
          // After the create request completes, there is a brief period where the security group might not be
          // available and subsequent operations on it will fail, so make sure it's there
          OperationPoller.retryWithBackoff(
            o -> appGroups.addAll(targetAmazonEC2.describeSecurityGroups(
              new DescribeSecurityGroupsRequest().withGroupIds(newGroupId)).getSecurityGroups()),
            200, 5);
        }
      }
      if (!dryRun) {
        String elbGroupId = elbGroup.getTarget().getTargetId();
        SecurityGroup appGroup = appGroups.stream().filter(isAppSecurityGroup()).findFirst().get();
        if (allowIngressFromClassic) {
          addClassicLinkIngress(targetLookup, getDeployDefaults().getClassicLinkSecurityGroupName(),
            appGroup.getGroupId(), target.getCredentials(), target.getVpcId());
        }
        boolean hasElbIngressPermission = appGroup.getIpPermissions().stream()
          .anyMatch(p -> p.getUserIdGroupPairs().stream().anyMatch(u -> u.getGroupId().equals(elbGroupId)));
        if (!hasElbIngressPermission) {
          sourceDescription.getListenerDescriptions().forEach(l -> {
            Listener listener = l.getListener();
            IpPermission newPermission = new IpPermission().withIpProtocol("tcp")
              .withFromPort(listener.getInstancePort()).withToPort(listener.getInstancePort())
              .withUserIdGroupPairs(new UserIdGroupPair().withGroupId(elbGroupId).withVpcId(target.getVpcId()));
            targetAmazonEC2.authorizeSecurityGroupIngress(new AuthorizeSecurityGroupIngressRequest()
              .withGroupId(appGroup.getGroupId())
              .withIpPermissions(newPermission)
            );
          });
        }
      }
    }
  }

  private Predicate<SecurityGroup> isAppSecurityGroup() {
    return g -> {
      if (!g.getGroupName().equals(applicationName)) {
        return false;
      }
      if (g.getVpcId() == null) {
        return target.getVpcId() == null;
      }
      return g.getVpcId().equals(target.getVpcId());
    };
  }

  // Adds a default public ingress for the load balancer. Called when migrating from Classic to VPC
  private void addPublicIngress(AmazonEC2 targetAmazonEC2, String elbGroupId, LoadBalancerDescription sourceDescription) {
    List<IpPermission> permissions = sourceDescription.getListenerDescriptions().stream().map(l -> new IpPermission()
      .withIpProtocol("tcp")
      .withFromPort(l.getListener().getLoadBalancerPort())
      .withToPort(l.getListener().getLoadBalancerPort())
      .withIpv4Ranges(new IpRange().withCidrIp("0.0.0.0/0"))
      //TODO(cfieber)-ipv6
    ).collect(Collectors.toList());

    targetAmazonEC2.authorizeSecurityGroupIngress(new AuthorizeSecurityGroupIngressRequest()
      .withGroupId(elbGroupId)
      .withIpPermissions(permissions)
    );
  }

  /**
   * Generates a list of security groups that should be applied to the target load balancer
   *
   * @param sourceDescription AWS descriptor of source load balancer
   * @param result            result object of the calling migate operation
   * @return the list of security groups that will be created or added, excluding the elb-specific security group
   */
  protected List<MigrateSecurityGroupResult> getTargetSecurityGroups(LoadBalancerDescription sourceDescription,
                                                                     MigrateLoadBalancerResult result) {
    sourceDescription.getSecurityGroups().stream()
      .filter(g -> !sourceLookup.getSecurityGroupById(source.getCredentialAccount(), g, source.getVpcId()).isPresent())
      .forEach(m -> result.getWarnings().add("Skipping creation of security group: " + m + " (could not be found in source location)"));
    List<SecurityGroup> currentGroups = sourceDescription.getSecurityGroups().stream()
      .filter(g -> sourceLookup.getSecurityGroupById(source.getCredentialAccount(), g, source.getVpcId()).isPresent())
      .map(g -> sourceLookup.getSecurityGroupById(source.getCredentialAccount(), g, source.getVpcId())
        .get().getSecurityGroup()).collect(Collectors.toList());

    return sourceDescription.getSecurityGroups().stream()
      .filter(g -> currentGroups.stream().anyMatch(g2 -> g2.getGroupId().equals(g)))
      .map(g -> {
        SecurityGroup match = currentGroups.stream().filter(g3 -> g3.getGroupId().equals(g)).findFirst().get();
        SecurityGroupLocation sourceLocation = new SecurityGroupLocation();
        sourceLocation.setName(match.getGroupName());
        sourceLocation.setRegion(source.getRegion());
        sourceLocation.setCredentials(source.getCredentials());
        sourceLocation.setVpcId(source.getVpcId());
        return new SecurityGroupMigrator(sourceLookup, targetLookup, migrateSecurityGroupStrategy,
          sourceLocation, new SecurityGroupLocation(target)).migrate(dryRun);
      })
      .collect(Collectors.toList());
  }

  private Vpc getVpc(LoadBalancerLocation source) {
    if (source.getVpcId() != null) {
      DescribeVpcsResult vpcLookup = getAmazonClientProvider().getAmazonEC2(source.getCredentials(), source.getRegion())
        .describeVpcs(new DescribeVpcsRequest().withVpcIds(source.getVpcId()));
      if (vpcLookup.getVpcs().isEmpty()) {
        throw new IllegalStateException(String.format("Could not find VPC %s in %s/%s",
          source.getVpcId(), source.getCredentialAccount(), source.getRegion()));
      }

      return vpcLookup.getVpcs().get(0);
    }

    return null;
  }

  /**
   * Generates the name of the new load balancer. By default, removes a number of suffixes, then adds the name
   * of the VPC (if any), and shrinks the load balancer name to 32 characters if necessary
   *
   * @param sourceName the base name
   * @param sourceVpc  the source VPC
   * @param targetVpc  the target VPC
   * @return the final name of the load balancer
   */
  protected String generateLoadBalancerName(String sourceName, Vpc sourceVpc, Vpc targetVpc) {
    String targetName = sourceName;
    targetName = removeSuffix(targetName, AmazonVpcProvider.getVpcName(sourceVpc));
    targetName = removeSuffix(targetName, "classic");
    targetName = removeSuffix(targetName, "frontend");
    targetName = removeSuffix(targetName, "vpc");
    if (targetVpc != null) {
      targetName += "-" + AmazonVpcProvider.getVpcName(targetVpc);
    }

    return shrinkName(targetName);
  }

  private String removeSuffix(String name, String suffix) {
    if (name.endsWith("-" + suffix)) {
      name = name.substring(0, name.length() - suffix.length() - 1);
    }
    return name;
  }

  /**
   * Reduces name to 32 characters
   *
   * @param name the name
   * @return the short version of the name
   */
  protected String shrinkName(String name) {
    final int MAX_LENGTH = 32;

    if (name.length() > MAX_LENGTH) {
      name = name
        .replace("-internal", "-int")
        .replace("-external", "-ext")
        .replace("-elb", "");
    }


    if (name.length() > MAX_LENGTH) {
      name = name
        .replace("-dev", "-d")
        .replace("-test", "-t")
        .replace("-prod", "-p")
        .replace("-main", "-m")
        .replace("-legacy", "-l")
        .replace("-backend", "-b")
        .replace("-front", "-f")
        .replace("-release", "-r")
        .replace("-private", "-p")
        .replace("-edge", "-e")
        .replace("-global", "-g");
    }


    if (name.length() > MAX_LENGTH) {
      name = name
        .replace("internal", "int")
        .replace("external", "ext")
        .replace("backend", "b")
        .replace("frontend", "f")
        .replace("east", "e")
        .replace("west", "w")
        .replace("north", "n")
        .replace("south", "s");
    }


    if (name.length() > MAX_LENGTH) {
      name = name.substring(0, MAX_LENGTH);
    }

    return name;
  }

}
