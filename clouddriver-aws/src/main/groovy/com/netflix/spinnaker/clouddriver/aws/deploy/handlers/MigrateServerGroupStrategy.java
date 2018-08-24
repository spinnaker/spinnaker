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

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.LaunchConfiguration;
import com.amazonaws.services.autoscaling.model.SuspendedProcess;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.config.AwsConfiguration.DeployDefaults;
import com.netflix.spinnaker.clouddriver.aws.deploy.converters.AllowLaunchAtomicOperationConverter;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription.Capacity;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription.Source;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.AllowLaunchAtomicOperation;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.LoadBalancerMigrator;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.LoadBalancerMigrator.LoadBalancerLocation;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.LoadBalancerMigrator.TargetLoadBalancerLocation;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.MigrateLoadBalancerResult;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.MigrateSecurityGroupResult;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupLookup;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupMigrator;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupMigrator.SecurityGroupLocation;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.servergroup.MigrateServerGroupResult;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.servergroup.ServerGroupMigrator.ServerGroupLocation;
import com.netflix.spinnaker.clouddriver.aws.deploy.validators.BasicAmazonDeployDescriptionValidator;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.services.AsgService;
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidationErrors;
import org.springframework.validation.Errors;

import java.util.*;
import java.util.stream.Collectors;

public abstract class MigrateServerGroupStrategy implements MigrateStrategySupport {

  protected SecurityGroupLookup sourceLookup;
  protected SecurityGroupLookup targetLookup;
  protected ServerGroupLocation source;
  protected ServerGroupLocation target;
  protected boolean allowIngressFromClassic;
  protected boolean dryRun;
  protected String subnetType;
  protected String elbSubnetType;
  protected Map<String, String> loadBalancerNameMapping;

  protected MigrateSecurityGroupStrategy migrateSecurityGroupStrategy;
  protected MigrateLoadBalancerStrategy getMigrateLoadBalancerStrategy;

  abstract AmazonClientProvider getAmazonClientProvider();

  abstract RegionScopedProviderFactory getRegionScopedProviderFactory();

  abstract DeployDefaults getDeployDefaults();

  abstract BasicAmazonDeployHandler getBasicAmazonDeployHandler();

  abstract BasicAmazonDeployDescriptionValidator getBasicAmazonDeployDescriptionValidator();

  abstract AllowLaunchAtomicOperationConverter getAllowLaunchAtomicOperationConverter();


  /**
   * Migrates a server group and its associated load balancers and security groups from one location to another
   *
   * @param source                       the source server group
   * @param target                       the target location in which to migrate
   * @param sourceLookup                 a security group lookup cache for the source region
   * @param targetLookup                 a security group lookup cache for the target region (may be the same object as the sourceLookup)
   * @param migrateLoadBalancerStrategy  the load balancer migration strategy
   * @param migrateSecurityGroupStrategy the security group migration strategy
   * @param subnetType                   the subnetType in which to migrate the server group (should be null for EC Classic migrations)
   * @param elbSubnetType                the subnetType in which to migrate load balancers
   * @param iamRole                      the iamRole to use when migrating (optional)
   * @param keyPair                      the keyPair to use when migrating (optional)
   * @param targetAmi                    the target imageId to use when migrating (optional)
   * @param loadBalancerNameMapping      a mapping of source-to-target load balancer names
   * @param allowIngressFromClassic      if subnetType is present, and this is true, and app security groups are created
   *                                     via the deployDefaults, will add broad (80-65535) ingress from the classic link
   *                                     security group
   * @param dryRun                       whether to perform the migration or simply calculate the migration
   * @return a result set indicating the components required to perform the migration (if a dry run), or the objects
   * updated by the migration (if not a dry run)
   */
  public synchronized MigrateServerGroupResult generateResults(ServerGroupLocation source, ServerGroupLocation target,
                                                               SecurityGroupLookup sourceLookup, SecurityGroupLookup targetLookup,
                                                               MigrateLoadBalancerStrategy migrateLoadBalancerStrategy,
                                                               MigrateSecurityGroupStrategy migrateSecurityGroupStrategy,
                                                               String subnetType, String elbSubnetType, String iamRole, String keyPair,
                                                               String targetAmi, Map<String, String> loadBalancerNameMapping,
                                                               boolean allowIngressFromClassic, boolean dryRun) {

    this.sourceLookup = sourceLookup;
    this.targetLookup = targetLookup;
    this.source = source;
    this.target = target;
    this.subnetType = subnetType;
    this.elbSubnetType = elbSubnetType;
    this.allowIngressFromClassic = allowIngressFromClassic;
    this.loadBalancerNameMapping = loadBalancerNameMapping;
    this.dryRun = dryRun;
    this.migrateSecurityGroupStrategy = migrateSecurityGroupStrategy;
    this.getMigrateLoadBalancerStrategy = migrateLoadBalancerStrategy;

    AsgService asgService = getRegionScopedProviderFactory().forRegion(source.getCredentials(), source.getRegion())
      .getAsgService();

    AutoScalingGroup sourceGroup = asgService.getAutoScalingGroup(source.getName());

    if (sourceGroup == null) {
      throw new IllegalStateException("Error retrieving source server group: " + source.getName());
    }

    LaunchConfiguration launchConfig = asgService.getLaunchConfiguration(sourceGroup.getLaunchConfigurationName());

    if (launchConfig == null) {
      throw new IllegalStateException("Could not find launch config: " + sourceGroup.getLaunchConfigurationName());
    }

    Names names = Names.parseName(source.getName());

    List<MigrateLoadBalancerResult> targetLoadBalancers = generateTargetLoadBalancers(sourceGroup);

    MigrateServerGroupResult migrateResult = new MigrateServerGroupResult();

    List<MigrateSecurityGroupResult> targetSecurityGroups = generateTargetSecurityGroups(launchConfig, migrateResult);

    Map<String, List<String>> zones = new HashMap<>();
    zones.put(target.getRegion(), target.getAvailabilityZones());

    DeploymentResult result;
    if (!dryRun) {
      Capacity capacity = getCapacity();
      BasicAmazonDeployDescription deployDescription = new BasicAmazonDeployDescription();
      deployDescription.setSource(getSource(source));
      deployDescription.setCredentials(target.getCredentials());
      deployDescription.setAmiName(targetAmi != null ? targetAmi : launchConfig.getImageId());
      deployDescription.setApplication(names.getApp());
      deployDescription.setStack(names.getStack());
      deployDescription.setFreeFormDetails(names.getDetail());
      deployDescription.setInstanceMonitoring(launchConfig.getInstanceMonitoring().getEnabled());
      deployDescription.setInstanceType(launchConfig.getInstanceType());
      deployDescription.setIamRole(iamRole != null ? iamRole : launchConfig.getIamInstanceProfile());
      deployDescription.setKeyPair(keyPair != null ? keyPair : launchConfig.getKeyName());
      deployDescription.setAssociatePublicIpAddress(launchConfig.getAssociatePublicIpAddress());
      deployDescription.setCooldown(sourceGroup.getDefaultCooldown());
      deployDescription.setHealthCheckGracePeriod(sourceGroup.getHealthCheckGracePeriod());
      deployDescription.setHealthCheckType(sourceGroup.getHealthCheckType());
      deployDescription.setSuspendedProcesses(sourceGroup.getSuspendedProcesses().stream()
        .map(SuspendedProcess::getProcessName).collect(Collectors.toSet()));
      deployDescription.setTerminationPolicies(sourceGroup.getTerminationPolicies());
      deployDescription.setKernelId(launchConfig.getKernelId());
      deployDescription.setEbsOptimized(launchConfig.getEbsOptimized());
      deployDescription.setLoadBalancers(targetLoadBalancers.stream()
        .map(MigrateLoadBalancerResult::getTargetName).collect(Collectors.toList()));
      deployDescription.setSecurityGroups(targetSecurityGroups.stream()
        .filter(sg -> !sg.getSkipped().contains(sg.getTarget()))
        .map(sg -> sg.getTarget().getTargetName()).collect(Collectors.toList()));
      deployDescription.setAvailabilityZones(zones);
      deployDescription.setStartDisabled(true);
      deployDescription.setCapacity(capacity);
      deployDescription.setSubnetType(subnetType);

      BasicAmazonDeployDescription description = generateDescription(deployDescription);

      if (!source.getCredentialAccount().equals(target.getCredentialAccount())) {
        Map<String, String> allowLaunchMap = new HashMap<>();
        allowLaunchMap.put("credentials", source.getCredentialAccount());
        allowLaunchMap.put("account", target.getCredentialAccount());
        allowLaunchMap.put("region", target.getRegion());
        allowLaunchMap.put("amiName", deployDescription.getAmiName());
        AllowLaunchAtomicOperation operation = getAllowLaunchAtomicOperationConverter().convertOperation(allowLaunchMap);

        operation.operate(null);
      }

      result = getBasicAmazonDeployHandler().handle(description, new ArrayList());
    } else {
      result = new DeploymentResult();
      String targetName = getRegionScopedProviderFactory().forRegion(target.getCredentials(), target.getRegion())
        .getAWSServerGroupNameResolver()
        .resolveNextServerGroupName(names.getApp(), names.getStack(), names.getDetail(), false);

      result.setServerGroupNames(Collections.singletonList(targetName));
    }
    migrateResult.setServerGroupNames(result.getServerGroupNames());
    migrateResult.setLoadBalancers(targetLoadBalancers);
    migrateResult.setSecurityGroups(targetSecurityGroups);
    return migrateResult;
  }

  private BasicAmazonDeployDescription generateDescription(BasicAmazonDeployDescription deployDescription) {
    BasicAmazonDeployDescription description = getBasicAmazonDeployHandler().copySourceAttributes(
      getRegionScopedProviderFactory().forRegion(source.getCredentials(), source.getRegion()), source.getName(),
      false, deployDescription);

    validateDeployDescription(description);

    return description;
  }

  private void validateDeployDescription(BasicAmazonDeployDescription description) {
    Errors errors = new DescriptionValidationErrors(description);

    getBasicAmazonDeployDescriptionValidator().validate(new ArrayList(), description, errors);

    if (errors.hasErrors()) {
      throw new IllegalStateException("Invalid deployment configuration. Errors: "
        + errors.getAllErrors().stream().flatMap(s -> Arrays.stream(s.getCodes())).collect(Collectors.toList()));
    }
  }

  private static Source getSource(ServerGroupLocation source) {
    Source deploySource = new Source();
    deploySource.setAccount(source.getCredentialAccount());
    deploySource.setRegion(source.getRegion());
    deploySource.setAsgName(source.getName());
    return deploySource;
  }

  private static Capacity getCapacity() {
    Capacity capacity = new Capacity();
    capacity.setMin(0);
    capacity.setMax(0);
    capacity.setDesired(0);
    return capacity;
  }

  protected List<MigrateSecurityGroupResult> generateTargetSecurityGroups(LaunchConfiguration sourceLaunchConfig,
                                                                          MigrateServerGroupResult result) {

    sourceLaunchConfig.getSecurityGroups().stream()
      .filter(g -> !sourceLookup.getSecurityGroupById(source.getCredentialAccount(), g, source.getVpcId()).isPresent())
      .forEach(m -> result.getWarnings().add("Skipping creation of security group: " + m
        + " (could not be found in source location)"));

    List<String> securityGroupNames = sourceLaunchConfig.getSecurityGroups().stream()
      .filter(g -> sourceLookup.getSecurityGroupById(source.getCredentialAccount(), g, source.getVpcId()).isPresent())
      .map(g -> sourceLookup.getSecurityGroupById(source.getCredentialAccount(), g, source.getVpcId()).get())
      .map(g -> g.getSecurityGroup().getGroupName())
      .collect(Collectors.toList());

    List<MigrateSecurityGroupResult> targetSecurityGroups = securityGroupNames.stream().map(group ->
      getMigrateSecurityGroupResult(group)
    ).collect(Collectors.toList());

    if (getDeployDefaults().getAddAppGroupToServerGroup()) {
      Names names = Names.parseName(source.getName());
      // if the app security group is already present, don't include it twice
      Optional<MigrateSecurityGroupResult> appGroup = targetSecurityGroups.stream()
        .filter(r -> names.getApp().equals(r.getTarget().getTargetName())).findFirst();
      if (!appGroup.isPresent()) {
        appGroup = Optional.of(generateAppSecurityGroup());
        targetSecurityGroups.add(appGroup.get());
      }
      handleClassicLinkIngress(appGroup.get().getTarget().getTargetId());
    }

    return targetSecurityGroups;
  }

  protected List<MigrateLoadBalancerResult> generateTargetLoadBalancers(AutoScalingGroup sourceGroup) {
    return sourceGroup.getLoadBalancerNames().stream()
      .map(this::getMigrateLoadBalancerResult)
      .collect(Collectors.toList());
  }

  protected MigrateSecurityGroupResult generateAppSecurityGroup() {
    Names names = Names.parseName(source.getName());
    SecurityGroupLocation appGroupLocation = new SecurityGroupLocation();
    appGroupLocation.setName(names.getApp());
    appGroupLocation.setRegion(source.getRegion());
    appGroupLocation.setCredentials(source.getCredentials());
    appGroupLocation.setVpcId(source.getVpcId());
    SecurityGroupMigrator migrator = new SecurityGroupMigrator(sourceLookup, targetLookup, migrateSecurityGroupStrategy,
      appGroupLocation, new SecurityGroupLocation(target));
    migrator.setCreateIfSourceMissing(true);
    MigrateSecurityGroupResult result = migrator.migrate(dryRun);
    handleClassicLinkIngress(result.getTarget().getTargetId());
    return result;
  }

  protected void handleClassicLinkIngress(String securityGroupId) {
    if (!dryRun && allowIngressFromClassic) {
      addClassicLinkIngress(targetLookup, getDeployDefaults().getClassicLinkSecurityGroupName(),
        securityGroupId, target.getCredentials(), target.getVpcId());
    }
  }

  private MigrateSecurityGroupResult getMigrateSecurityGroupResult(String group) {
    SecurityGroupLocation sourceLocation = new SecurityGroupLocation();
    sourceLocation.setName(group);
    sourceLocation.setRegion(source.getRegion());
    sourceLocation.setCredentials(source.getCredentials());
    sourceLocation.setVpcId(source.getVpcId());
    return new SecurityGroupMigrator(sourceLookup, targetLookup, migrateSecurityGroupStrategy,
      sourceLocation, new SecurityGroupLocation(target)).migrate(dryRun);
  }

  private MigrateLoadBalancerResult getMigrateLoadBalancerResult(String lbName) {
    Names names = Names.parseName(source.getName());
    LoadBalancerLocation sourceLocation = new LoadBalancerLocation();
    sourceLocation.setName(lbName);
    sourceLocation.setRegion(source.getRegion());
    sourceLocation.setVpcId(source.getVpcId());
    sourceLocation.setCredentials(source.getCredentials());
    TargetLoadBalancerLocation loadBalancerTarget = new TargetLoadBalancerLocation(sourceLocation, target);
    if (loadBalancerNameMapping.containsKey(lbName)) {
      loadBalancerTarget.setName(loadBalancerNameMapping.get(lbName));
    }
    return new LoadBalancerMigrator(sourceLookup, targetLookup, getAmazonClientProvider(), getRegionScopedProviderFactory(),
      migrateSecurityGroupStrategy, getDeployDefaults(), getMigrateLoadBalancerStrategy, sourceLocation,
      loadBalancerTarget, elbSubnetType, names.getApp(), allowIngressFromClassic).migrate(dryRun);
  }
}
