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
import com.netflix.spinnaker.clouddriver.aws.AwsConfiguration.DeployDefaults;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription.Capacity;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.BasicAmazonDeployDescription.Source;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.LoadBalancerMigrator;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.LoadBalancerMigrator.LoadBalancerLocation;
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
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.validation.Errors;

import java.util.*;
import java.util.stream.Collectors;

public abstract class MigrateServerGroupStrategy {

  protected SecurityGroupLookup sourceLookup;
  protected SecurityGroupLookup targetLookup;
  protected MigrateSecurityGroupStrategy migrateSecurityGroupStrategy;
  protected MigrateLoadBalancerStrategy getMigrateLoadBalancerStrategy;

  abstract AmazonClientProvider getAmazonClientProvider();

  abstract RegionScopedProviderFactory getRegionScopedProviderFactory();

  abstract DeployDefaults getDeployDefaults();

  abstract BasicAmazonDeployHandler getBasicAmazonDeployHandler();

  abstract BasicAmazonDeployDescriptionValidator getBasicAmazonDeployDescriptionValidator();


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
   * @param iamRole                      the iamRole to use when migrating (optional)
   * @param keyPair                      the keyPair to use when migrating (optional)
   * @param targetAmi                    the target imageId to use when migrating (optional)
   * @param dryRun                       whether to perform the migration or simply calculate the migration
   * @return a result set indicating the components required to perform the migration (if a dry run), or the objects
   * updated by the migration (if not a dry run)
   */
  public synchronized MigrateServerGroupResult generateResults(ServerGroupLocation source, ServerGroupLocation target,
                                                  SecurityGroupLookup sourceLookup, SecurityGroupLookup targetLookup,
                                                  MigrateLoadBalancerStrategy migrateLoadBalancerStrategy,
                                                  MigrateSecurityGroupStrategy migrateSecurityGroupStrategy,
                                                  String subnetType, String iamRole, String keyPair, String targetAmi,
                                                  boolean dryRun) {

    this.sourceLookup = sourceLookup;
    this.targetLookup = targetLookup;
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

    List<MigrateLoadBalancerResult> targetLoadBalancers = generateTargetLoadBalancers(
      sourceGroup, source, target, subnetType, dryRun);

    MigrateServerGroupResult migrateResult = new MigrateServerGroupResult();

    List<MigrateSecurityGroupResult> targetSecurityGroups = generateTargetSecurityGroups(launchConfig, source,  target,
      migrateResult, dryRun);

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
      deployDescription.setBase64UserData(launchConfig.getUserData());
      deployDescription.setLoadBalancers(targetLoadBalancers.stream()
        .map(MigrateLoadBalancerResult::getTargetName).collect(Collectors.toList()));
      deployDescription.setSecurityGroups(targetSecurityGroups.stream()
        .map(sg -> sg.getTarget().getTargetName()).collect(Collectors.toList()));
      deployDescription.setAvailabilityZones(zones);
      deployDescription.setStartDisabled(true);
      deployDescription.setCapacity(capacity);
      deployDescription.setSubnetType(subnetType);

      BasicAmazonDeployDescription description = generateDescription(source, deployDescription);

      result = getBasicAmazonDeployHandler().handle(description, new ArrayList());
    } else {
      result = new DeploymentResult();
      String targetName = getRegionScopedProviderFactory().forRegion(target.getCredentials(), target.getRegion())
        .getAWSServerGroupNameResolver()
        .resolveNextServerGroupName(names.getApp(), names.getStack(), names.getDetail(), false);

      result.setServerGroupNames(Collections.singletonList(targetName));
    }
    migrateResult.setServerGroupName(result.getServerGroupNames().get(0));
    migrateResult.setLoadBalancers(targetLoadBalancers);
    migrateResult.setSecurityGroups(targetSecurityGroups);
    return migrateResult;
  }

  private BasicAmazonDeployDescription generateDescription(ServerGroupLocation source,
                                                           BasicAmazonDeployDescription deployDescription) {
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
        + errors.getAllErrors().stream().flatMap(s -> Arrays.asList(s.getCodes()).stream()).collect(Collectors.toList()));
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
                                                                          ServerGroupLocation source,
                                                                          ServerGroupLocation target,
                                                                          MigrateServerGroupResult result,
                                                                          boolean dryRun) {

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
      getMigrateSecurityGroupResult(source, target, sourceLookup, targetLookup, dryRun, group)
    ).collect(Collectors.toList());

    if (getDeployDefaults().getAddAppGroupToServerGroup()) {
      targetSecurityGroups.add(generateAppSecurityGroup(source, target, sourceLookup, targetLookup, dryRun));
    }

    return targetSecurityGroups;
  }

  protected List<MigrateLoadBalancerResult> generateTargetLoadBalancers(AutoScalingGroup sourceGroup,
                                                                        ServerGroupLocation source,
                                                                        ServerGroupLocation target,
                                                                        String subnetType,
                                                                        boolean dryRun) {
    return sourceGroup.getLoadBalancerNames().stream().map(lbName ->
      getMigrateLoadBalancerResult(source, target, sourceLookup, targetLookup, subnetType, dryRun, lbName)
    ).collect(Collectors.toList());
  }

  protected MigrateSecurityGroupResult generateAppSecurityGroup(ServerGroupLocation source, ServerGroupLocation target,
                                                                SecurityGroupLookup sourceLookup,
                                                                SecurityGroupLookup targetLookup, boolean dryRun) {
    Names names = Names.parseName(source.getName());
    SecurityGroupLocation appGroupLocation = new SecurityGroupLocation();
    appGroupLocation.setName(names.getApp());
    appGroupLocation.setRegion(source.getRegion());
    appGroupLocation.setCredentials(source.getCredentials());
    appGroupLocation.setVpcId(source.getVpcId());
    SecurityGroupMigrator migrator = new SecurityGroupMigrator(sourceLookup, targetLookup, migrateSecurityGroupStrategy,
      appGroupLocation, new SecurityGroupLocation(target));
    migrator.setCreateIfSourceMissing(true);
    return migrator.migrate(dryRun);
  }

  private MigrateSecurityGroupResult getMigrateSecurityGroupResult(ServerGroupLocation source,
                                                                   ServerGroupLocation target,
                                                                   SecurityGroupLookup sourceLookup,
                                                                   SecurityGroupLookup targetLookup,
                                                                   boolean dryRun, String group) {
    SecurityGroupLocation sourceLocation = new SecurityGroupLocation();
    sourceLocation.setName(group);
    sourceLocation.setRegion(source.getRegion());
    sourceLocation.setCredentials(source.getCredentials());
    sourceLocation.setVpcId(source.getVpcId());
    return new SecurityGroupMigrator(sourceLookup, targetLookup, migrateSecurityGroupStrategy,
      sourceLocation, new SecurityGroupLocation(target)).migrate(dryRun);
  }

  private MigrateLoadBalancerResult getMigrateLoadBalancerResult(ServerGroupLocation source, ServerGroupLocation target,
                                                                 SecurityGroupLookup sourceLookup,
                                                                 SecurityGroupLookup targetLookup, String subnetType,
                                                                 boolean dryRun, String lbName) {
    Names names = Names.parseName(source.getName());
    LoadBalancerLocation sourceLocation = new LoadBalancerLocation();
    sourceLocation.setName(lbName);
    sourceLocation.setRegion(source.getRegion());
    sourceLocation.setVpcId(source.getVpcId());
    sourceLocation.setCredentials(source.getCredentials());
    return new LoadBalancerMigrator(sourceLookup, targetLookup, getAmazonClientProvider(), getRegionScopedProviderFactory(),
      migrateSecurityGroupStrategy, getDeployDefaults(), getMigrateLoadBalancerStrategy, sourceLocation,
      new LoadBalancerLocation(target), subnetType, names.getApp()).migrate(dryRun);
  }
}
