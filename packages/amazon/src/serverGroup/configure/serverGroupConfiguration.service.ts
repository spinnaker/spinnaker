import { module } from 'angular';
import {
  chain,
  clone,
  cloneDeep,
  extend,
  find,
  flatten,
  has,
  intersection,
  keys,
  map,
  partition,
  some,
  xor,
} from 'lodash';
import { $q } from 'ngimport';

import {
  AccountService,
  Application,
  CACHE_INITIALIZER_SERVICE,
  CacheInitializerService,
  IAccountDetails,
  IDeploymentStrategy,
  IRegion,
  ISecurityGroup,
  IServerGroupCommand,
  IServerGroupCommandBackingData,
  IServerGroupCommandBackingDataFiltered,
  IServerGroupCommandDirty,
  IServerGroupCommandResult,
  IServerGroupCommandViewState,
  ISubnet,
  LOAD_BALANCER_READ_SERVICE,
  LoadBalancerReader,
  NameUtils,
  SECURITY_GROUP_READER,
  SecurityGroupReader,
  SERVER_GROUP_COMMAND_REGISTRY_PROVIDER,
  ServerGroupCommandRegistry,
  setMatchingResourceSummary,
  SubnetReader,
} from '@spinnaker/core';

import { AWSProviderSettings } from '../../aws.settings';
import { AutoScalingProcessService } from '../details/scalingProcesses/AutoScalingProcessService';
import {
  IAmazonLoadBalancerSourceData,
  IApplicationLoadBalancerSourceData,
  IKeyPair,
  IScalingProcess,
} from '../../domain';
import { AMAZON_INSTANCE_AWSINSTANCETYPE_SERVICE } from '../../instance/awsInstanceType.service';
import { KeyPairsReader } from '../../keyPairs';

export type IBlockDeviceMappingSource = 'source' | 'ami' | 'default';

export interface IAmazonServerGroupCommandDirty extends IServerGroupCommandDirty {
  targetGroups?: string[];
}

export interface IAmazonServerGroupCommandResult extends IServerGroupCommandResult {
  dirty: IAmazonServerGroupCommandDirty;
}

export interface IAmazonServerGroupCommandBackingDataFiltered extends IServerGroupCommandBackingDataFiltered {
  keyPairs: string[];
  targetGroups: string[];
}

export interface IAmazonServerGroupCommandBackingData extends IServerGroupCommandBackingData {
  appLoadBalancers: IAmazonLoadBalancerSourceData[];
  filtered: IAmazonServerGroupCommandBackingDataFiltered;
  keyPairs: IKeyPair[];
  targetGroups: string[];
  scalingProcesses: IScalingProcess[];
}

export interface IAmazonServerGroupCommandViewState extends IServerGroupCommandViewState {
  dirty: IAmazonServerGroupCommandDirty;
  spelTargetGroups: string[];
  spelLoadBalancers: string[];
}

export interface IAmazonServerGroupCommand extends IServerGroupCommand {
  associateIPv6Address?: boolean;
  associatePublicIpAddress: boolean;
  backingData: IAmazonServerGroupCommandBackingData;
  copySourceCustomBlockDeviceMappings: boolean;
  ebsOptimized: boolean;
  healthCheckGracePeriod: number;
  instanceMonitoring: boolean;
  keyPair: string;
  legacyUdf?: boolean;
  requireIMDSv2?: boolean;
  spotPrice: string;
  targetHealthyDeployPercentage: number;
  useAmiBlockDeviceMappings: boolean;
  targetGroups: string[];
  setLaunchTemplate?: boolean;
  unlimitedCpuCredits?: boolean;
  capacityRebalance?: boolean;
  viewState: IAmazonServerGroupCommandViewState;

  getBlockDeviceMappingsSource: (command: IServerGroupCommand) => IBlockDeviceMappingSource;
  selectBlockDeviceMappingsSource: (command: IServerGroupCommand, selection: string) => void;
  usePreferredZonesChanged: (command: IServerGroupCommand) => IAmazonServerGroupCommandResult;
  regionIsDeprecated: (command: IServerGroupCommand) => boolean;
}

export class AwsServerGroupConfigurationService {
  private enabledMetrics = [
    'GroupMinSize',
    'GroupMaxSize',
    'GroupDesiredCapacity',
    'GroupInServiceInstances',
    'GroupPendingInstances',
    'GroupStandbyInstances',
    'GroupTerminatingInstances',
    'GroupTotalInstances',
  ];
  private healthCheckTypes = ['EC2', 'ELB'];
  private terminationPolicies = [
    'OldestInstance',
    'NewestInstance',
    'OldestLaunchConfiguration',
    'ClosestToNextInstanceHour',
    'Default',
  ];

  public static $inject = [
    'securityGroupReader',
    'awsInstanceTypeService',
    'cacheInitializer',
    'loadBalancerReader',
    'serverGroupCommandRegistry',
  ];
  constructor(
    private securityGroupReader: SecurityGroupReader,
    private awsInstanceTypeService: any,
    private cacheInitializer: CacheInitializerService,
    private loadBalancerReader: LoadBalancerReader,
    private serverGroupCommandRegistry: ServerGroupCommandRegistry,
  ) {}

  public configureUpdateCommand(command: IAmazonServerGroupCommand): void {
    command.backingData = {
      enabledMetrics: clone(this.enabledMetrics),
      healthCheckTypes: clone(this.healthCheckTypes),
      terminationPolicies: clone(this.terminationPolicies),
    } as IAmazonServerGroupCommandBackingData;
  }

  public configureCommand(application: Application, cmd: IAmazonServerGroupCommand): PromiseLike<void> {
    this.applyOverrides('beforeConfiguration', cmd);
    // TODO: Instead of attaching these to the command itself, they could be static methods
    cmd.toggleSuspendedProcess = (command: IAmazonServerGroupCommand, process: string): void => {
      command.suspendedProcesses = command.suspendedProcesses || [];
      const processIndex = command.suspendedProcesses.indexOf(process);
      if (processIndex === -1) {
        command.suspendedProcesses = command.suspendedProcesses.concat(process);
      } else {
        command.suspendedProcesses = command.suspendedProcesses.filter((p) => p !== process);
      }
    };

    cmd.processIsSuspended = (command: IAmazonServerGroupCommand, process: string): boolean =>
      command.suspendedProcesses.includes(process);

    cmd.onStrategyChange = (command: IAmazonServerGroupCommand, strategy: IDeploymentStrategy): void => {
      if (AWSProviderSettings.serverGroups?.enableLaunchTemplates) {
        command.setLaunchTemplate = strategy.key === 'rollingpush' ? true : undefined;
      }

      // Any strategy other than None or Custom should force traffic to be enabled
      if (strategy.key !== '' && strategy.key !== 'custom') {
        command.suspendedProcesses = (command.suspendedProcesses || []).filter((p) => p !== 'AddToLoadBalancer');
      }
    };

    cmd.getBlockDeviceMappingsSource = (command: IAmazonServerGroupCommand): IBlockDeviceMappingSource => {
      if (command.copySourceCustomBlockDeviceMappings) {
        return 'source';
      } else if (command.useAmiBlockDeviceMappings) {
        return 'ami';
      }
      return 'default';
    };

    cmd.selectBlockDeviceMappingsSource = (command: IAmazonServerGroupCommand, selection: string): void => {
      if (selection === 'source') {
        // copy block device mappings from source asg
        command.copySourceCustomBlockDeviceMappings = true;
        command.useAmiBlockDeviceMappings = false;
      } else if (selection === 'ami') {
        // use block device mappings from selected ami
        command.copySourceCustomBlockDeviceMappings = false;
        command.useAmiBlockDeviceMappings = true;
      } else {
        // use default block device mappings for selected instance type
        command.copySourceCustomBlockDeviceMappings = false;
        command.useAmiBlockDeviceMappings = false;
      }
    };

    cmd.regionIsDeprecated = (command: IAmazonServerGroupCommand): boolean => {
      return (
        has(command, 'backingData.filtered.regions') &&
        command.backingData.filtered.regions.some((region) => region.name === command.region && region.deprecated)
      );
    };

    return $q
      .all([
        AccountService.getCredentialsKeyedByAccount('aws'),
        this.securityGroupReader.getAllSecurityGroups(),
        SubnetReader.listSubnets(),
        AccountService.getPreferredZonesByAccount('aws'),
        KeyPairsReader.listKeyPairs(),
        this.awsInstanceTypeService.getAllTypesByRegion(),
        $q.when(clone(this.enabledMetrics)),
        $q.when(clone(this.healthCheckTypes)),
        $q.when(clone(this.terminationPolicies)),
      ])
      .then(
        ([
          credentialsKeyedByAccount,
          securityGroups,
          subnets,
          preferredZones,
          keyPairs,
          instanceTypes,
          enabledMetrics,
          healthCheckTypes,
          terminationPolicies,
        ]) => {
          const backingData: Partial<IAmazonServerGroupCommandBackingData> = {
            credentialsKeyedByAccount,
            securityGroups,
            subnets,
            preferredZones,
            keyPairs,
            instanceTypes,
            enabledMetrics,
            healthCheckTypes,
            terminationPolicies,
          };

          let securityGroupReloader: PromiseLike<void> = $q.when();
          backingData.accounts = keys(backingData.credentialsKeyedByAccount);
          backingData.filtered = {} as IAmazonServerGroupCommandBackingDataFiltered;
          backingData.scalingProcesses = AutoScalingProcessService.listProcesses();
          backingData.appLoadBalancers = application.getDataSource('loadBalancers').data;
          backingData.managedResources = application.getDataSource('managedResources')?.data?.resources;
          cmd.backingData = backingData as IAmazonServerGroupCommandBackingData;
          this.configureVpcId(cmd);
          backingData.filtered.securityGroups = this.getRegionalSecurityGroups(cmd);
          if (cmd.viewState.disableImageSelection) {
            this.configureInstanceTypes(cmd);
          }

          if (cmd.securityGroups && cmd.securityGroups.length) {
            const regionalSecurityGroupIds = map(this.getRegionalSecurityGroups(cmd), 'id');
            if (intersection(cmd.securityGroups, regionalSecurityGroupIds).length < cmd.securityGroups.length) {
              securityGroupReloader = this.refreshSecurityGroups(cmd, true);
            }
          }

          return securityGroupReloader.then(() => {
            this.applyOverrides('afterConfiguration', cmd);
            this.attachEventHandlers(cmd);
          });
        },
      );
  }

  public applyOverrides(phase: string, command: IAmazonServerGroupCommand): void {
    this.serverGroupCommandRegistry.getCommandOverrides('aws').forEach((override: any) => {
      if (override[phase]) {
        override[phase](command);
      }
    });
  }

  public configureKeyPairs(command: IAmazonServerGroupCommand): IServerGroupCommandResult {
    const result: IAmazonServerGroupCommandResult = { dirty: {} };
    if (command.credentials && command.region) {
      // isDefault is imperfect, since we don't know what the previous account/region was, but probably a safe bet
      const isDefault = some<any>(
        command.backingData.credentialsKeyedByAccount,
        (c) =>
          c.defaultKeyPair &&
          command.keyPair &&
          command.keyPair.indexOf(c.defaultKeyPair.replace('{{region}}', '')) === 0,
      );
      const filtered = chain(command.backingData.keyPairs)
        .filter({ account: command.credentials, region: command.region })
        .map('keyName')
        .value();
      if (command.keyPair && filtered.length && !filtered.includes(command.keyPair)) {
        const acct: IAccountDetails =
          command.backingData.credentialsKeyedByAccount[command.credentials] ||
          ({
            regions: [],
            defaultKeyPair: null,
          } as IAccountDetails);
        if (acct.defaultKeyPair) {
          // {{region}} is the only supported substitution pattern
          const defaultKeyPair = acct.defaultKeyPair.replace('{{region}}', command.region);
          if (isDefault && filtered.includes(defaultKeyPair)) {
            command.keyPair = defaultKeyPair;
          } else {
            command.keyPair = null;
            result.dirty.keyPair = true;
          }
        } else {
          command.keyPair = null;
          result.dirty.keyPair = true;
        }
      }
      command.backingData.filtered.keyPairs = filtered;
    } else {
      command.backingData.filtered.keyPairs = [];
    }
    return result;
  }

  public configureInstanceTypes(command: IAmazonServerGroupCommand): IServerGroupCommandResult {
    const result: IAmazonServerGroupCommandResult = { dirty: {} };
    if (command.region && (command.virtualizationType || command.viewState.disableImageSelection)) {
      let filtered = this.awsInstanceTypeService.getAvailableTypesForRegions(command.backingData.instanceTypes, [
        command.region,
      ]);
      if (command.virtualizationType) {
        filtered = this.awsInstanceTypeService.filterInstanceTypes(
          filtered,
          command.virtualizationType,
          !!command.vpcId,
        );
      }
      if (command.instanceType && !filtered.includes(command.instanceType)) {
        result.dirty.instanceType = command.instanceType;
        command.instanceType = null;
      }
      command.backingData.filtered.instanceTypes = filtered;
    } else {
      command.backingData.filtered.instanceTypes = [];
    }
    extend(command.viewState.dirty, result.dirty);
    return result;
  }

  public configureImages(command: IAmazonServerGroupCommand): IServerGroupCommandResult {
    const result: IAmazonServerGroupCommandResult = { dirty: {} };
    if (!command.amiName) {
      command.virtualizationType = null;
    }
    if (command.viewState.disableImageSelection) {
      return result;
    }
    if (command.amiName && !command.region) {
      result.dirty.amiName = true;
      command.amiName = null;
    }
    return result;
  }

  public configureAvailabilityZones(command: IAmazonServerGroupCommand): void {
    command.backingData.filtered.availabilityZones = find<IRegion>(
      command.backingData.credentialsKeyedByAccount[command.credentials].regions,
      { name: command.region },
    ).availabilityZones;
  }

  public configureSubnetPurposes(command: IAmazonServerGroupCommand): IServerGroupCommandResult {
    const result: IAmazonServerGroupCommandResult = { dirty: {} };
    const filteredData = command.backingData.filtered;
    if (command.region === null) {
      return result;
    }
    filteredData.subnetPurposes = chain(command.backingData.subnets)
      .filter({ account: command.credentials, region: command.region })
      .reject({ target: 'elb' })
      .reject({ purpose: null })
      .uniqBy('purpose')
      .value();

    if (!chain(filteredData.subnetPurposes).some({ purpose: command.subnetType }).value()) {
      command.subnetType = null;
      result.dirty.subnetType = true;
    }
    return result;
  }

  public getRegionalSecurityGroups(command: IAmazonServerGroupCommand): ISecurityGroup[] {
    const newSecurityGroups = command.backingData.securityGroups[command.credentials] || { aws: {} };
    return chain(newSecurityGroups.aws[command.region])
      .filter({ vpcId: command.vpcId || null })
      .sortBy('name')
      .value();
  }

  public configureSecurityGroupOptions(command: IAmazonServerGroupCommand): IServerGroupCommandResult {
    const result: IAmazonServerGroupCommandResult = { dirty: {} };
    const currentOptions: ISecurityGroup[] = command.backingData.filtered.securityGroups;
    const newRegionalSecurityGroups = this.getRegionalSecurityGroups(command);
    const isExpression =
      typeof command.securityGroups === 'string' && (command.securityGroups as string).includes('${');
    if (currentOptions && command.securityGroups && !isExpression) {
      // not initializing - we are actually changing groups
      const currentGroupNames = command.securityGroups.map((groupId) => {
        const match = find(currentOptions, { id: groupId });
        return match ? match.name : groupId;
      });

      const matchedGroups = command.securityGroups
        .map((groupId) => {
          const securityGroup = find(currentOptions, { id: groupId }) || find(currentOptions, { name: groupId });
          return securityGroup ? securityGroup.name : null;
        })
        .map((groupName) => find(newRegionalSecurityGroups, { name: groupName }))
        .filter((group) => group);

      const matchedGroupNames = map(matchedGroups, 'name');
      const removed = xor(currentGroupNames, matchedGroupNames);
      command.securityGroups = map(matchedGroups, 'id');
      if (removed.length) {
        result.dirty.securityGroups = removed;
      }
    }
    command.backingData.filtered.securityGroups = newRegionalSecurityGroups.sort((a, b) => {
      return a.name.localeCompare(b.name);
    });
    return result;
  }

  public refreshSecurityGroups(
    command: IAmazonServerGroupCommand,
    skipCommandReconfiguration?: boolean,
  ): PromiseLike<void> {
    return this.cacheInitializer.refreshCache('securityGroups').then(() => {
      return this.securityGroupReader.getAllSecurityGroups().then((securityGroups) => {
        command.backingData.securityGroups = securityGroups;
        if (!skipCommandReconfiguration) {
          this.configureSecurityGroupOptions(command);
        }
      });
    });
  }

  private getLoadBalancerMap(command: IAmazonServerGroupCommand): IAmazonLoadBalancerSourceData[] {
    if (command.backingData.loadBalancers) {
      return chain(command.backingData.loadBalancers)
        .map('accounts')
        .flattenDeep()
        .filter({ name: command.credentials })
        .map('regions')
        .flattenDeep()
        .filter({ name: command.region })
        .map<IAmazonLoadBalancerSourceData>('loadBalancers')
        .flattenDeep<IAmazonLoadBalancerSourceData>()
        .value();
    }

    const appLoadBalancers = command.backingData.appLoadBalancers || [];
    return appLoadBalancers.filter((lb) => lb.region === command.region && lb.account === command.credentials);
  }

  public getLoadBalancerNames(command: IAmazonServerGroupCommand): string[] {
    const loadBalancers = this.getLoadBalancerMap(command).filter(
      (lb) => (!lb.loadBalancerType || lb.loadBalancerType === 'classic') && lb.vpcId === command.vpcId,
    );
    return loadBalancers.map((lb) => lb.name).sort();
  }

  public getVpcLoadBalancerNames(command: IAmazonServerGroupCommand): string[] {
    const loadBalancersForVpc = this.getLoadBalancerMap(command).filter(
      (lb) => (!lb.loadBalancerType || lb.loadBalancerType === 'classic') && lb.vpcId,
    );
    return loadBalancersForVpc.map((lb) => lb.name).sort();
  }

  public getTargetGroupNames(command: IAmazonServerGroupCommand): string[] {
    const loadBalancersV2 = this.getLoadBalancerMap(command).filter(
      (lb) => lb.loadBalancerType !== 'classic',
    ) as IApplicationLoadBalancerSourceData[];
    const instanceTargetGroups = flatten(
      loadBalancersV2.map<any>((lb) => lb.targetGroups.filter((tg) => tg.targetType === 'instance')),
    );
    return instanceTargetGroups.map((tg) => tg.name).sort();
  }

  private getValidMatches(all: string[], current: string[]): { valid: string[]; invalid: string[]; spel: string[] } {
    const spel = current.filter((v) => v.includes('${'));
    const matched = intersection(all, current);
    const [valid, invalid] = partition(current, (c) => matched.includes(c) || spel.includes(c));
    return { valid, invalid, spel };
  }

  public configureLoadBalancerOptions(command: IAmazonServerGroupCommand): IServerGroupCommandResult {
    const result: IAmazonServerGroupCommandResult = { dirty: {} };
    const currentLoadBalancers = (command.loadBalancers || []).concat(command.vpcLoadBalancers || []);
    const currentTargetGroups = command.targetGroups || [];
    const newLoadBalancers = this.getLoadBalancerNames(command);
    const vpcLoadBalancers = this.getVpcLoadBalancerNames(command);
    const allTargetGroups = this.getTargetGroupNames(command);

    if (currentLoadBalancers && command.loadBalancers) {
      const allValidLoadBalancers = command.vpcId ? newLoadBalancers : newLoadBalancers.concat(vpcLoadBalancers);
      const { valid, invalid, spel } = this.getValidMatches(allValidLoadBalancers, currentLoadBalancers);
      command.loadBalancers = intersection(newLoadBalancers.concat(spel), valid);
      if (!command.vpcId) {
        command.vpcLoadBalancers = intersection(vpcLoadBalancers, valid);
      } else {
        delete command.vpcLoadBalancers;
      }
      if (invalid.length) {
        result.dirty.loadBalancers = invalid;
      }
      command.viewState.spelLoadBalancers = spel || [];
    }

    if (currentTargetGroups && command.targetGroups && !currentTargetGroups.includes('${')) {
      const { valid, invalid, spel } = this.getValidMatches(allTargetGroups, currentTargetGroups);
      command.targetGroups = valid;
      if (invalid.length) {
        result.dirty.targetGroups = invalid;
      }
      command.viewState.spelTargetGroups = spel || [];
    }

    command.backingData.filtered.loadBalancers = newLoadBalancers;
    command.backingData.filtered.vpcLoadBalancers = vpcLoadBalancers;
    command.backingData.filtered.targetGroups = allTargetGroups;
    return result;
  }

  public refreshLoadBalancers(command: IAmazonServerGroupCommand, skipCommandReconfiguration?: boolean) {
    return this.loadBalancerReader.listLoadBalancers('aws').then((loadBalancers) => {
      command.backingData.loadBalancers = loadBalancers;
      if (!skipCommandReconfiguration) {
        this.configureLoadBalancerOptions(command);
      }
    });
  }

  public configureVpcId(command: IAmazonServerGroupCommand): IAmazonServerGroupCommandResult {
    const result: IAmazonServerGroupCommandResult = { dirty: {} };
    if (!command.subnetType) {
      command.vpcId = null;
      result.dirty.vpcId = true;
    } else {
      const subnet = find<ISubnet>(command.backingData.subnets, {
        purpose: command.subnetType,
        account: command.credentials,
        region: command.region,
      });
      command.vpcId = subnet ? subnet.vpcId : null;
    }
    extend(result.dirty, this.configureInstanceTypes(command).dirty);
    return result;
  }

  // TODO: Instead of attaching these to the command itself, they could be static methods
  public attachEventHandlers(cmd: IAmazonServerGroupCommand): void {
    cmd.usePreferredZonesChanged = (command: IAmazonServerGroupCommand): IAmazonServerGroupCommandResult => {
      const currentZoneCount = command.availabilityZones ? command.availabilityZones.length : 0;
      const result: IAmazonServerGroupCommandResult = { dirty: {} };
      const preferredZonesForAccount = command.backingData.preferredZones[command.credentials];
      if (preferredZonesForAccount && preferredZonesForAccount[command.region] && command.viewState.usePreferredZones) {
        command.availabilityZones = cloneDeep(preferredZonesForAccount[command.region].sort());
      } else {
        command.availabilityZones = intersection(
          command.availabilityZones,
          command.backingData.filtered.availabilityZones,
        );
        const newZoneCount = command.availabilityZones ? command.availabilityZones.length : 0;
        if (currentZoneCount !== newZoneCount) {
          result.dirty.availabilityZones = true;
        }
      }
      return result;
    };

    cmd.subnetChanged = (command: IAmazonServerGroupCommand): IServerGroupCommandResult => {
      const result = this.configureVpcId(command);
      extend(result.dirty, this.configureSecurityGroupOptions(command).dirty);
      extend(result.dirty, this.configureLoadBalancerOptions(command).dirty);
      command.viewState.dirty = command.viewState.dirty || {};
      extend(command.viewState.dirty, result.dirty);
      return result;
    };

    cmd.regionChanged = (command: IAmazonServerGroupCommand): IServerGroupCommandResult => {
      const result: IAmazonServerGroupCommandResult = { dirty: {} };
      const filteredData = command.backingData.filtered;
      extend(result.dirty, this.configureSubnetPurposes(command).dirty);
      if (command.region) {
        extend(result.dirty, command.subnetChanged(command).dirty);
        extend(result.dirty, this.configureInstanceTypes(command).dirty);

        this.configureAvailabilityZones(command);
        extend(result.dirty, command.usePreferredZonesChanged(command).dirty);

        extend(result.dirty, this.configureImages(command).dirty);
        extend(result.dirty, this.configureKeyPairs(command).dirty);
      } else {
        filteredData.regionalAvailabilityZones = null;
      }
      setMatchingResourceSummary(command);
      return result;
    };

    cmd.clusterChanged = (command: IAmazonServerGroupCommand): void => {
      command.moniker = NameUtils.getMoniker(command.application, command.stack, command.freeFormDetails);
      setMatchingResourceSummary(command);
    };

    cmd.credentialsChanged = (command: IAmazonServerGroupCommand): IServerGroupCommandResult => {
      const result: IAmazonServerGroupCommandResult = { dirty: {} };
      const backingData = command.backingData;
      if (command.credentials) {
        const regionsForAccount: IAccountDetails =
          backingData.credentialsKeyedByAccount[command.credentials] ||
          ({ regions: [], defaultKeyPair: null } as IAccountDetails);
        backingData.filtered.regions = regionsForAccount.regions;
        if (!some(backingData.filtered.regions, { name: command.region })) {
          command.region = null;
          result.dirty.region = true;
        } else {
          extend(result.dirty, command.regionChanged(command).dirty);
        }
      } else {
        command.region = null;
      }
      setMatchingResourceSummary(command);
      return result;
    };

    cmd.imageChanged = (command: IAmazonServerGroupCommand): IServerGroupCommandResult =>
      this.configureInstanceTypes(command);

    cmd.instanceTypeChanged = (command: IAmazonServerGroupCommand): void => {
      command.ebsOptimized = this.awsInstanceTypeService.isEbsOptimized(command.instanceType);
    };

    this.applyOverrides('attachEventHandlers', cmd);
  }
}

export const AWS_SERVER_GROUP_CONFIGURATION_SERVICE = 'spinnaker.amazon.serverGroup.configure.service';
module(AWS_SERVER_GROUP_CONFIGURATION_SERVICE, [
  SECURITY_GROUP_READER,
  AMAZON_INSTANCE_AWSINSTANCETYPE_SERVICE,
  LOAD_BALANCER_READ_SERVICE,
  CACHE_INITIALIZER_SERVICE,
  SERVER_GROUP_COMMAND_REGISTRY_PROVIDER,
]).service('awsServerGroupConfigurationService', AwsServerGroupConfigurationService);
