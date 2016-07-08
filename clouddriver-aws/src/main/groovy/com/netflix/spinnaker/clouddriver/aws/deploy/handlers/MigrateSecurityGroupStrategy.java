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
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.UserIdGroupPair;
import com.amazonaws.services.ec2.model.Vpc;
import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.AbstractAmazonCredentialsDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertSecurityGroupDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.MigrateSecurityGroupReference;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.MigrateSecurityGroupResult;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupIngressConverter;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupLookup;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupLookupFactory.SecurityGroupUpdater;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.securitygroup.SecurityGroupMigrator.SecurityGroupLocation;
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonVpcProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;

import java.util.*;
import java.util.stream.Collectors;

public abstract class MigrateSecurityGroupStrategy {

  protected SecurityGroupLookup sourceLookup;
  protected SecurityGroupLookup targetLookup;

  abstract AmazonClientProvider getAmazonClientProvider();

  /**
   * Infrastructure applications are treated as optional, non-managed resources when performing migrations
   * @return
     */
  abstract List<String> getInfrastructureApplications();

  /**
   * Generates a result set, describing the actions required to migrate the source security group to the target.
   *
   * @param source                the source security group
   * @param target                the target location
   * @param sourceLookup          a lookup cache for the source region
   * @param targetLookup          a lookup cache for the target region (may be the same as the sourceLookup)
   * @param createIfSourceMissing indicates whether the operation should proceed if the source does not already exist;
   *                              if false and the source group cannot be found, an IllegalStateException will be thrown
   * @return the result set
   */
  public synchronized MigrateSecurityGroupResult generateResults(SecurityGroupLocation source, SecurityGroupLocation target,
                                                    SecurityGroupLookup sourceLookup, SecurityGroupLookup targetLookup,
                                                    boolean createIfSourceMissing, boolean dryRun) {

    this.sourceLookup = sourceLookup;
    this.targetLookup = targetLookup;

    final MigrateSecurityGroupResult result = new MigrateSecurityGroupResult();
    final Optional<SecurityGroupUpdater> sourceUpdater = sourceLookup.getSecurityGroupByName(
      source.getCredentialAccount(),
      source.getName(),
      source.getVpcId()
    );
    if (!createIfSourceMissing && !sourceUpdater.isPresent()) {
      throw new IllegalStateException("Security group does not exist: " + source.toString());
    }

    Set<MigrateSecurityGroupReference> targetReferences = new HashSet<>();
    String sourceGroupId = null;
    if (sourceUpdater.isPresent()) {
      sourceGroupId = sourceUpdater.get().getSecurityGroup().getGroupId();
      targetReferences = getTargetReferences(sourceUpdater.get());

      // convert names before determining if rules are important
      targetReferences.stream().forEach(reference -> reference.setTargetName(getTargetName(reference.getSourceName())));

      result.setErrors(shouldError(target, targetReferences));
      if (!result.getErrors().isEmpty()) {
        return result;
      }

      result.setSkipped(shouldSkipWithoutWarning(target, targetReferences));
      Set<MigrateSecurityGroupReference> toCheck = new HashSet<>(targetReferences);
      toCheck.removeAll(result.getSkipped());

      result.setWarnings(shouldWarn(target, toCheck));
      toCheck.removeAll(result.getWarnings());
    }
    result.setTarget(createTargetSecurityGroupReference(source, target, sourceGroupId));

    Set<MigrateSecurityGroupReference> toVerify = new HashSet<>(targetReferences);
    toVerify.add(result.getTarget());
    toVerify.removeAll(result.getWarnings());
    toVerify.removeAll(result.getSkipped());

    result.setCreated(shouldCreate(target, toVerify));

    List<MigrateSecurityGroupReference> reused = new ArrayList<>(toVerify);
    reused.removeAll(result.getCreated());
    result.setReused(reused);

    if (!dryRun) {
      performMigration(result, source, target);
    }

    return result;
  }

  private void performMigration(MigrateSecurityGroupResult results, SecurityGroupLocation source,
                                SecurityGroupLocation target) {
    final Optional<SecurityGroupUpdater> sourceGroupUpdater = sourceLookup.getSecurityGroupByName(
      source.getCredentialAccount(),
      source.getName(),
      source.getVpcId());

    final SecurityGroup securityGroup = sourceGroupUpdater.isPresent() ? sourceGroupUpdater.get().getSecurityGroup() : null;

    Set<MigrateSecurityGroupReference> targetGroups = new HashSet<>(results.getCreated());
    targetGroups.addAll(results.getReused());
    if (!results.targetExists()) {
      targetGroups.add(results.getTarget());
    }
    results.getCreated().stream()
      .forEach(r -> r.setTargetId(
        createDependentSecurityGroup(r, source, target).getSecurityGroup().getGroupId()));

    Optional<SecurityGroupUpdater> targetGroup = targetLookup.getSecurityGroupByName(
      target.getCredentialAccount(),
      results.getTarget().getTargetName(),
      target.getVpcId()
    );

    if (!targetGroup.isPresent()) {
      throw new IllegalStateException("Target group cannot be found: " + results.getTarget().getTargetName());
    }

    if (sourceGroupUpdater.isPresent() && shouldCreateTargetPermissions(sourceGroupUpdater.get().getSecurityGroup())) {
      createTargetPermissions(securityGroup, targetGroup.get(), targetGroups, results);
    }
    results.getTarget().setTargetId(targetGroup.get().getSecurityGroup().getGroupId());
  }

  /**
   * Returns references to all security groups that should be created for the target
   *
   * @param target     the target security group
   * @param references the collection of potential security groups to select from; implementations can choose to provide
   *                   additional security groups that are *not* members of this set
   * @return a list of security groups that need to be created in order to migrate the target security group
   */
  protected Set<MigrateSecurityGroupReference> shouldCreate(SecurityGroupLocation target,
                                                         Set<MigrateSecurityGroupReference> references) {

    List<NetflixAmazonCredentials> credentials = references.stream()
      .map(AbstractAmazonCredentialsDescription::getCredentials).distinct().collect(Collectors.toList());
    Map<String, String> vpcMappings = getVpcMappings(target, credentials);

    return references.stream().distinct().filter(reference -> {
      String targetVpc = vpcMappings.get(reference.getAccountId());
      reference.setVpcId(targetVpc);

      Optional<SecurityGroupUpdater> targetMatch =
        targetLookup.getSecurityGroupByName(reference.getCredentialAccount(), reference.getTargetName(), targetVpc);
      if (targetMatch.isPresent()) {
        reference.setTargetId(targetMatch.get().getSecurityGroup().getGroupId());
        return false;
      }
      return true;
    }).collect(Collectors.toSet());
  }

  /**
   * Returns references to all security groups that should halt the migration
   *
   * @param target     the target security group
   * @param references the collection of potential security groups to select from; implementations can choose to provide
   *                   additional security groups that are *not* members of this set
   * @return a list of security groups that will fail the migration; if this call returns anything, additional checks
   * will not run
   */
  protected Set<MigrateSecurityGroupReference> shouldError(SecurityGroupLocation target,
                                                        Set<MigrateSecurityGroupReference> references) {
    return new HashSet<>();
  }

  /**
   * Returns references to all security groups that should be created for the target
   *
   * @param target     the target security group
   * @param references the collection of potential security groups to select from; implementations can choose to provide
   *                   additional security groups that are *not* members of this set
   * @return a list of security groups that need to be created in order to migrate the target security group
   */
  protected Set<MigrateSecurityGroupReference> shouldWarn(SecurityGroupLocation target,
                                                       Set<MigrateSecurityGroupReference> references) {
    return references.stream().filter(reference -> {
      if (!targetLookup.accountIdExists(reference.getAccountId())) {
        reference.setExplanation("Spinnaker does not manage the account " + reference.getAccountId());
        return true;
      }
      return false;
    }).collect(Collectors.toSet());
  }

  /**
   * Returns references to all security groups that will be skipped - but not visually reported to the user - when
   * migrating the target. This includes security groups
   *
   * @param target     the target security group
   * @param references the collection of potential security groups to select from; implementations can choose to provide
   *                   additional security groups that are *not* members of this set
   * @return a list of security groups that will be skipped when migrating the target security group
   */
  protected Set<MigrateSecurityGroupReference> shouldSkipWithoutWarning(SecurityGroupLocation target,
                                                                     Set<MigrateSecurityGroupReference> references) {

    return references.stream().filter(reference -> {
      if (reference.getAccountId().equals("amazon-elb") && target.getVpcId() != null) {
        reference.setExplanation("amazon-elb groups are not required in VPC environments");
        return true;
      }
      String targetName = reference.getTargetName();
      String targetApp = Names.parseName(targetName).getApp();
      String account = targetLookup.getAccountNameForId(reference.getAccountId());
      if (getInfrastructureApplications().contains(targetApp) &&
        !targetLookup.getSecurityGroupByName(account, targetName, reference.getVpcId()).isPresent()) {
        reference.setExplanation(targetName + " does not exist in destination");
        return true;
      }
      return false;
    }).collect(Collectors.toSet());
  }

  /**
   * Determines whether ingress rules should be updated when migrating the security group - for example, you may
   * not want to touch security groups that are managed by a different team, or security groups in a specific service
   *
   * @param securityGroup the security group
   * @return true if ingress rules should be updated, false otherwise
   */
  protected boolean shouldCreateTargetPermissions(SecurityGroup securityGroup) {
    return !getInfrastructureApplications().contains(Names.parseName(securityGroup.getGroupName()).getApp());
  }

  /**
   * Returns the name of the target security group based on the name of the source security group. Useful (at least at
   * Netflix) when migrating to remove legacy naming conventions
   *
   * @param sourceName the source name
   * @return the target name
   */
  protected String getTargetName(String sourceName) {
    String targetName = sourceName;
    if (targetName.endsWith("-vpc")) {
      targetName = targetName.substring(0, targetName.length() - 4);
    }
    return targetName;
  }

  private Set<MigrateSecurityGroupReference> getTargetReferences(SecurityGroupUpdater source) {
    SecurityGroup group = source.getSecurityGroup();
    if (getInfrastructureApplications().contains(Names.parseName(group.getGroupName()).getApp())) {
      return new HashSet<>();
    }
    return group.getIpPermissions()
      .stream()
      .map(IpPermission::getUserIdGroupPairs)
      .flatMap(List::stream)
      .filter(pair -> !pair.getGroupId().equals(group.getGroupId()) || !pair.getUserId().equals(group.getOwnerId()))
      .map(pair -> {
        NetflixAmazonCredentials account = sourceLookup.getCredentialsForId(pair.getUserId());
        if (pair.getGroupName() == null) {
          sourceLookup.getSecurityGroupById(account.getName(), pair.getGroupId(), pair.getVpcId())
            .ifPresent(u -> pair.setGroupName(u.getSecurityGroup().getGroupName()));
        }
        return new MigrateSecurityGroupReference(pair, account);
      })
      .collect(Collectors.toSet());
  }

  private MigrateSecurityGroupReference createTargetSecurityGroupReference(SecurityGroupLocation source,
                                                                           SecurityGroupLocation target,
                                                                           String sourceGroupId) {
    MigrateSecurityGroupReference ref = new MigrateSecurityGroupReference();
    ref.setSourceName(source.getName());
    ref.setAccountId(target.getCredentials().getAccountId());
    ref.setVpcId(target.getVpcId());
    ref.setSourceId(sourceGroupId);
    ref.setCredentials(target.getCredentials());
    ref.setTargetName(getTargetName(source.getName()));
    return ref;
  }

  private Map<String, String> getVpcMappings(SecurityGroupLocation target, List<NetflixAmazonCredentials> accounts) {
    Map<String, String> mappings = new HashMap<>();
    if (target.getVpcId() == null) {
      return mappings;
    }
    AmazonEC2 baseTarget = getAmazonClientProvider().getAmazonEC2(target.getCredentials(), target.getRegion());
    Vpc targetVpc = baseTarget.describeVpcs().getVpcs().stream()
      .filter(vpc -> vpc.getVpcId().equals(target.getVpcId()))
      .findFirst().orElse(null);

    String targetName = AmazonVpcProvider.getVpcName(targetVpc);

    accounts.stream().forEach(account -> {
      List<Vpc> vpcs = getAmazonClientProvider().getAmazonEC2(account, target.getRegion()).describeVpcs().getVpcs();
      Vpc match = vpcs.stream()
        .filter(vpc -> AmazonVpcProvider.getVpcName(vpc).equals(targetName))
        .findFirst().orElse(null);
      mappings.put(account.getAccountId(), match.getVpcId());
    });
    return mappings;
  }

  // Creates a security group in the target location with no ingress rules
  private SecurityGroupUpdater createDependentSecurityGroup(MigrateSecurityGroupReference reference,
                                                            SecurityGroupLocation source,
                                                            SecurityGroupLocation target) {
    String sourceAccount = sourceLookup.getAccountNameForId(reference.getAccountId());
    NetflixAmazonCredentials targetCredentials = sourceAccount.equals(source.getCredentialAccount()) ?
      target.getCredentials() :
      sourceLookup.getCredentialsForName(sourceAccount);
    Optional<SecurityGroupUpdater> sourceGroup = sourceLookup.getSecurityGroupByName(sourceAccount, reference.getTargetName(), reference.getVpcId());
    String description = "Security group " + reference.getTargetName();
    if (sourceGroup.isPresent()) {
      description = sourceGroup.get().getSecurityGroup().getDescription();
    }
    UpsertSecurityGroupDescription upsertDescription = new UpsertSecurityGroupDescription();
    upsertDescription.setName(reference.getTargetName());
    upsertDescription.setCredentials(targetCredentials);
    upsertDescription.setDescription(description);
    upsertDescription.setVpcId(target.getVpcId());

    return targetLookup.createSecurityGroup(upsertDescription);
  }

  private void createTargetPermissions(SecurityGroup sourceGroup,
                                       SecurityGroupUpdater targetGroup,
                                       Set<MigrateSecurityGroupReference> targetGroups,
                                       MigrateSecurityGroupResult results) {

    List<IpPermission> targetPermissions = SecurityGroupIngressConverter
      .flattenPermissions(sourceGroup.getIpPermissions())
      .stream()
      .map(p -> {
        p.setUserIdGroupPairs(p.getUserIdGroupPairs().stream().map(UserIdGroupPair::clone).collect(Collectors.toList()));
        return p;
      })
      .filter(p -> p.getUserIdGroupPairs().isEmpty() ||
        p.getUserIdGroupPairs().stream().allMatch(pair -> targetGroups.stream()
          .anyMatch(g -> g.getSourceId().equals(pair.getGroupId()))))
      .collect(Collectors.toList());

    targetPermissions.forEach(permission ->
      permission.getUserIdGroupPairs().forEach(pair -> {
        MigrateSecurityGroupReference targetReference = targetGroups.stream().filter(group ->
          group.getSourceId().equals(pair.getGroupId())
        ).findFirst().get();
        pair.setGroupId(targetReference.getTargetId());
        pair.setGroupName(null);
        pair.setVpcId(targetReference.getVpcId());
      })
    );

    filterOutExistingRules(targetPermissions, targetGroup.getSecurityGroup());
    results.setIngressUpdates(targetPermissions.stream().filter(p -> !p.getUserIdGroupPairs().isEmpty() || !p.getIpRanges().isEmpty()).collect(Collectors.toList()));

    if (!results.getIngressUpdates().isEmpty()) {
      targetGroup.addIngress(targetPermissions);
    }
  }

  private void filterOutExistingRules(List<IpPermission> permissionsToApply, SecurityGroup targetGroup) {
    permissionsToApply.forEach(permission -> {
      permission.getUserIdGroupPairs().removeIf(pair ->
        targetGroup.getIpPermissions().stream().anyMatch(targetPermission ->
          targetPermission.getFromPort().equals(permission.getFromPort())
            && targetPermission.getToPort().equals(permission.getToPort())
            && targetPermission.getUserIdGroupPairs().stream().anyMatch(t -> t.getGroupId().equals(pair.getGroupId()))
        )
      );
      permission.getIpRanges().removeIf(range ->
        targetGroup.getIpPermissions().stream().anyMatch(targetPermission ->
          targetPermission.getFromPort().equals(permission.getFromPort())
            && targetPermission.getToPort().equals(permission.getToPort())
            && targetPermission.getIpRanges().contains(range)
        )
      );
    });
  }

}
