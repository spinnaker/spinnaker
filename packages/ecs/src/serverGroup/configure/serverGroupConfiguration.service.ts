import { IQService, module } from 'angular';
import { chain, clone, extend, find, flatten, has, intersection, keys, some, xor } from 'lodash';

import { IAmazonLoadBalancer } from '@spinnaker/amazon';
import {
  AccountService,
  CACHE_INITIALIZER_SERVICE,
  IAccountDetails,
  IArtifact,
  IDeploymentStrategy,
  IPipeline,
  IRegion,
  IServerGroupCommand,
  IServerGroupCommandBackingData,
  IServerGroupCommandBackingDataFiltered,
  IServerGroupCommandDirty,
  IServerGroupCommandResult,
  IServerGroupCommandViewState,
  IStage,
  ISubnet,
  LOAD_BALANCER_READ_SERVICE,
  LoadBalancerReader,
  NameUtils,
  SECURITY_GROUP_READER,
  SecurityGroupReader,
  SERVER_GROUP_COMMAND_REGISTRY_PROVIDER,
  ServerGroupCommandRegistry,
  SubnetReader,
} from '@spinnaker/core';
import { DockerImageReader, IDockerImage } from '@spinnaker/docker';

import { IEcsCapacityProviderDetails } from '../../ecsCluster/IEcsCapacityProviderDetails';
import { IEcsClusterDescriptor } from '../../ecsCluster/IEcsCluster';
import { EcsClusterReader } from '../../ecsCluster/ecsCluster.read.service';
import { IRoleDescriptor } from '../../iamRoles/IRole';
import { IamRoleReader } from '../../iamRoles/iamRole.read.service';
import { IMetricAlarmDescriptor } from '../../metricAlarm/MetricAlarm';
import { MetricAlarmReader } from '../../metricAlarm/metricAlarm.read.service';
import { IPlacementStrategy } from '../../placementStrategy/IPlacementStrategy';
import { PlacementStrategyService } from '../../placementStrategy/placementStrategy.service';
import { ISecretDescriptor } from '../../secrets/ISecret';
import { SecretReader } from '../../secrets/secret.read.service';
import { IServiceDiscoveryRegistryDescriptor } from '../../serviceDiscovery/IServiceDiscovery';
import { ServiceDiscoveryReader } from '../../serviceDiscovery/serviceDiscovery.read.service';

export interface IEcsServerGroupCommandDirty extends IServerGroupCommandDirty {
  targetGroups?: string[];
  defaulCapacityProviders?: string[];
  customCapacityProviders?: string[];
}

export interface IEcsServerGroupCommandResult extends IServerGroupCommandResult {
  dirty: IEcsServerGroupCommandDirty;
}

export interface IEcsDockerImage extends IDockerImage {
  imageId: string;
  message: string;
  fromTrigger: boolean;
  fromContext: boolean;
  stageId: string;
  imageLabelOrSha: string;
}

export interface IEcsServerGroupCommandViewState extends IServerGroupCommandViewState {
  contextImages: IEcsDockerImage[];
  pipeline: IPipeline;
  currentStage: IStage;
  dirty: IEcsServerGroupCommandDirty;
}

export interface IEcsServerGroupCommandBackingDataFiltered extends IServerGroupCommandBackingDataFiltered {
  targetGroups: string[];
  iamRoles: string[];
  ecsClusters: string[];
  availableCapacityProviders: string[];
  defaultCapacityProviderStrategy: IEcsCapacityProviderStrategyItem[];
  metricAlarms: IMetricAlarmDescriptor[];
  subnetTypes: ISubnet[];
  securityGroupNames: string[];
  secrets: string[];
  serviceDiscoveryRegistries: IServiceDiscoveryRegistryDescriptor[];
  images: IEcsDockerImage[];
}

export interface IEcsServerGroupCommandBackingData extends IServerGroupCommandBackingData {
  filtered: IEcsServerGroupCommandBackingDataFiltered;
  targetGroups: string[];
  ecsClusters: IEcsClusterDescriptor[];
  capacityProviderDetails: IEcsCapacityProviderDetails[];
  iamRoles: IRoleDescriptor[];
  metricAlarms: IMetricAlarmDescriptor[];
  launchTypes: string[];
  networkModes: string[];
  secrets: ISecretDescriptor[];
  serviceDiscoveryRegistries: IServiceDiscoveryRegistryDescriptor[];
  images: IEcsDockerImage[];
}

export interface IEcsTaskDefinitionArtifact {
  artifact?: IArtifact;
  artifactId?: string;
}

export interface IEcsContainerMapping {
  containerName: string;
  imageDescription: IEcsDockerImage;
}

export interface IEcsTargetGroupMapping {
  containerName: string;
  containerPort: number;
  targetGroup: string;
}

export interface IEcsServiceDiscoveryRegistryAssociation {
  registry: IServiceDiscoveryRegistryDescriptor;
  containerPort: number;
  containerName: string;
}

export interface IEcsCapacityProviderStrategyItem {
  capacityProvider: string;
  base: number;
  weight: number;
}

export interface IEcsServerGroupCommand extends IServerGroupCommand {
  associatePublicIpAddress: boolean;
  backingData: IEcsServerGroupCommandBackingData;
  computeUnits: number;
  reservedMemory: number;
  targetHealthyDeployPercentage: number;
  targetGroup: string;
  containerPort: number;
  placementStrategyName: string;
  placementStrategySequence: IPlacementStrategy[];
  imageDescription: IEcsDockerImage;
  networkMode: string;
  subnetType: string;
  subnetTypes: string[];
  securityGroups: string[];
  securityGroupNames: string[];
  viewState: IEcsServerGroupCommandViewState;
  taskDefinitionArtifact: IEcsTaskDefinitionArtifact;
  taskDefinitionArtifactAccount: string;
  containerMappings: IEcsContainerMapping[];
  loadBalancedContainer: string;
  targetGroupMappings: IEcsTargetGroupMapping[];
  serviceDiscoveryAssociations: IEcsServiceDiscoveryRegistryAssociation[];
  useTaskDefinitionArtifact: boolean;
  evaluateTaskDefinitionArtifactExpressions: boolean;

  capacityProviderStrategy: IEcsCapacityProviderStrategyItem[];
  useDefaultCapacityProviders: boolean;
  ecsClusterName: string;
  subnetTypeChanged: (command: IEcsServerGroupCommand) => IServerGroupCommandResult;
  placementStrategyNameChanged: (command: IEcsServerGroupCommand) => IServerGroupCommandResult;
  regionIsDeprecated: (command: IEcsServerGroupCommand) => boolean;

  clusterChanged: (command: IServerGroupCommand) => void;
}

export class EcsServerGroupConfigurationService {
  // private enabledMetrics = ['GroupMinSize', 'GroupMaxSize', 'GroupDesiredCapacity', 'GroupInServiceInstances', 'GroupPendingInstances', 'GroupStandbyInstances', 'GroupTerminatingInstances', 'GroupTotalInstances'];
  // private healthCheckTypes = ['EC2', 'ELB'];
  // private terminationPolicies = ['OldestInstance', 'NewestInstance', 'OldestLaunchConfiguration', 'ClosestToNextInstanceHour', 'Default'];
  private launchTypes = ['EC2', 'FARGATE'];
  private networkModes = ['bridge', 'host', 'awsvpc', 'none', 'default'];

  public static $inject = [
    '$q',
    'loadBalancerReader',
    'serverGroupCommandRegistry',
    'iamRoleReader',
    'ecsClusterReader',
    'metricAlarmReader',
    'placementStrategyService',
    'securityGroupReader',
    'secretReader',
  ];
  constructor(
    private $q: IQService,
    private loadBalancerReader: LoadBalancerReader,
    private serverGroupCommandRegistry: ServerGroupCommandRegistry,
    private iamRoleReader: IamRoleReader,
    private ecsClusterReader: EcsClusterReader,
    private metricAlarmReader: MetricAlarmReader,
    private placementStrategyService: PlacementStrategyService,
    private securityGroupReader: SecurityGroupReader,
    private secretReader: SecretReader,
  ) {}

  public configureUpdateCommand(command: IEcsServerGroupCommand): void {
    command.backingData = {
      // terminationPolicies: clone(this.terminationPolicies)
      launchTypes: clone(this.launchTypes),
      networkModes: clone(this.networkModes),
    } as IEcsServerGroupCommandBackingData;
  }

  // TODO (Bruno Carrier): Why do we need to inject an Application into this constructor so that the app works?  This is strange, and needs investigating
  public configureCommand(cmd: IEcsServerGroupCommand, imageQuery = ''): PromiseLike<void> {
    this.applyOverrides('beforeConfiguration', cmd);
    cmd.toggleSuspendedProcess = (command: IEcsServerGroupCommand, process: string): void => {
      command.suspendedProcesses = command.suspendedProcesses || [];
      const processIndex = command.suspendedProcesses.indexOf(process);
      if (processIndex === -1) {
        command.suspendedProcesses.push(process);
      } else {
        command.suspendedProcesses.splice(processIndex, 1);
      }
    };

    cmd.processIsSuspended = (command: IEcsServerGroupCommand, process: string): boolean =>
      command.suspendedProcesses.includes(process);

    cmd.onStrategyChange = (command: IEcsServerGroupCommand, strategy: IDeploymentStrategy): void => {
      // Any strategy other than None or Custom should force traffic to be enabled
      if (strategy.key !== '' && strategy.key !== 'custom') {
        command.suspendedProcesses = (command.suspendedProcesses || []).filter((p) => p !== 'AddToLoadBalancer');
      }
    };

    cmd.regionIsDeprecated = (command: IEcsServerGroupCommand): boolean => {
      return (
        has(command, 'backingData.filtered.regions') &&
        command.backingData.filtered.regions.some((region) => region.name === command.region && region.deprecated)
      );
    };

    const imageQueries = cmd.imageDescription ? [this.grabImageAndTag(cmd.imageDescription.imageId)] : [];

    if (imageQuery) {
      imageQueries.push(imageQuery);
    }

    let imagesPromise;
    if (imageQueries.length) {
      imagesPromise = this.$q
        .all(
          imageQueries.map((q) =>
            DockerImageReader.findImages({
              provider: 'dockerRegistry',
              count: 50,
              q: q,
            }),
          ),
        )
        .then((promises) => flatten(promises));
    } else {
      imagesPromise = this.$q.when([]);
    }

    return this.$q
      .all({
        credentialsKeyedByAccount: AccountService.getCredentialsKeyedByAccount('ecs'),
        loadBalancers: this.loadBalancerReader.listLoadBalancers('ecs'),
        subnets: SubnetReader.listSubnetsByProvider('ecs'),
        iamRoles: this.iamRoleReader.listRoles('ecs'),
        ecsClusters: this.ecsClusterReader.listClusters(),
        capacityProviderDetails: this.ecsClusterReader.describeClusters(cmd.credentials, cmd.region),
        metricAlarms: this.metricAlarmReader.listMetricAlarms(),
        securityGroups: this.securityGroupReader.getAllSecurityGroups(),
        launchTypes: this.$q.when(clone(this.launchTypes)),
        networkModes: this.$q.when(clone(this.networkModes)),
        secrets: this.secretReader.listSecrets(),
        serviceDiscoveryRegistries: ServiceDiscoveryReader.listServiceDiscoveryRegistries(),
        images: imagesPromise,
      })
      .then((backingData: Partial<IEcsServerGroupCommandBackingData>) => {
        backingData.accounts = keys(backingData.credentialsKeyedByAccount);
        backingData.filtered = {} as IEcsServerGroupCommandBackingDataFiltered;

        if (cmd.viewState.contextImages) {
          backingData.images = backingData.images.concat(cmd.viewState.contextImages);
        }
        cmd.backingData = backingData as IEcsServerGroupCommandBackingData;
        this.configureVpcId(cmd);
        this.configureAvailableIamRoles(cmd);
        this.configureAvailableSubnetTypes(cmd);
        this.configureAvailableSecurityGroups(cmd);
        this.configureAvailableEcsClusters(cmd);
        this.setCapacityProviderDetails(cmd);
        this.configureAvailableSecrets(cmd);
        this.configureAvailableServiceDiscoveryRegistries(cmd);
        this.configureAvailableImages(cmd);
        this.configureAvailableRegions(cmd);
        this.configureLoadBalancerOptions(cmd);
        this.applyOverrides('afterConfiguration', cmd);
        this.attachEventHandlers(cmd);
      });
  }

  public setCapacityProviderDetails(command: IEcsServerGroupCommand): void {
    this.$q
      .all({
        capacityProviderDetails: this.ecsClusterReader.describeClusters(command.credentials, command.region),
      })
      .then((result: Partial<IEcsServerGroupCommandBackingData>) => {
        command.backingData.capacityProviderDetails = chain(result.capacityProviderDetails)
          .map((cluster) => this.mapCapacityProviderDetails(cluster))
          .value();
      });

    if (command.ecsClusterName != null && command.ecsClusterName.length > 0) {
      this.configureAvailableCapacityProviders(command);
    } else {
      command.backingData.filtered.availableCapacityProviders = [];
      command.backingData.filtered.defaultCapacityProviderStrategy = [];
    }
    this.checkDirtyCapacityProviders(command);
  }

  public configureAvailableCapacityProviders(command: IEcsServerGroupCommand): void {
    command.backingData.filtered.availableCapacityProviders = chain(command.backingData.capacityProviderDetails)
      .filter({
        clusterName: command.ecsClusterName,
      })
      .map((availableCPs) => availableCPs.capacityProviders)
      .flattenDeep<string>()
      .value();

    command.backingData.filtered.defaultCapacityProviderStrategy = chain(command.backingData.capacityProviderDetails)
      .filter({
        clusterName: command.ecsClusterName,
      })
      .map((availableCPs) => availableCPs.defaultCapacityProviderStrategy)
      .flattenDeep<IEcsCapacityProviderStrategyItem>()
      .value();
  }

  public mapCapacityProviderDetails(describeClusters: IEcsCapacityProviderDetails): IEcsCapacityProviderDetails {
    return {
      capacityProviders: describeClusters.capacityProviders,
      clusterName: describeClusters.clusterName,
      defaultCapacityProviderStrategy: describeClusters.defaultCapacityProviderStrategy,
    };
  }

  public applyOverrides(phase: string, command: IEcsServerGroupCommand): void {
    this.serverGroupCommandRegistry.getCommandOverrides('ecs').forEach((override: any) => {
      if (override[phase]) {
        override[phase](command);
      }
    });
  }

  public grabImageAndTag(imageId: string): string {
    return imageId.split('/').pop();
  }

  public buildImageId(image: IEcsDockerImage): string {
    if (image.fromContext) {
      return `${image.imageLabelOrSha}`;
    } else if (image.fromTrigger && !image.tag) {
      return `${image.registry}/${image.repository} (Tag resolved at runtime)`;
    } else {
      return `${image.registry}/${image.repository}:${image.tag}`;
    }
  }

  public mapImage(image: IEcsDockerImage): IEcsDockerImage {
    if (image.message !== undefined) {
      return image;
    }

    return {
      repository: image.repository,
      tag: image.tag,
      imageId: this.buildImageId(image),
      registry: image.registry,
      fromContext: image.fromContext,
      fromTrigger: image.fromTrigger,
      account: image.account,
      imageLabelOrSha: image.imageLabelOrSha,
      stageId: image.stageId,
      message: image.message,
    };
  }

  public configureAvailableImages(command: IEcsServerGroupCommand): void {
    // No filtering required, but need to decorate with the displayable image ID
    command.backingData.filtered.images = command.backingData.images.map((image) => this.mapImage(image));
  }

  public configureAvailabilityZones(command: IEcsServerGroupCommand): void {
    command.backingData.filtered.availabilityZones = find<IRegion>(
      command.backingData.credentialsKeyedByAccount[command.credentials].regions,
      { name: command.region },
    ).availabilityZones;
    command.availabilityZones = command.backingData.filtered.availabilityZones;
  }

  public configureAvailableSecurityGroups(command: IEcsServerGroupCommand): void {
    if (command.subnetType == null && (command.subnetTypes == null || command.subnetTypes.length == 0)) {
      command.backingData.filtered.securityGroups = [];
      return;
    }

    const vpcId = chain(command.backingData.subnets)
      .filter({
        account: command.credentials,
        region: command.region,
        purpose: command.subnetTypes ? command.subnetTypes[0] : command.subnetType,
      })
      .compact()
      .uniqBy('purpose')
      .map('vpcId')
      .value()[0];

    if (
      command.backingData.securityGroups[command.credentials] &&
      command.backingData.securityGroups[command.credentials]['ecs'] &&
      command.backingData.securityGroups[command.credentials]['ecs'][command.region]
    ) {
      const allSecurityGroups = command.backingData.securityGroups[command.credentials]['ecs'][command.region];
      command.backingData.filtered.securityGroupNames = chain(allSecurityGroups)
        .filter({ vpcId: vpcId })
        .map('name')
        .value() as string[];
    }
  }

  public configureAvailableSubnetTypes(command: IEcsServerGroupCommand): void {
    command.backingData.filtered.subnetTypes = chain(command.backingData.subnets)
      .filter({
        account: command.credentials,
        region: command.region,
      })
      .compact()
      .uniqBy('purpose')
      .value()
      .filter(function (e: ISubnet) {
        return e.purpose && e.purpose.length > 0;
      });
  }

  public configureAvailableEcsClusters(command: IEcsServerGroupCommand): void {
    command.backingData.filtered.ecsClusters = chain(command.backingData.ecsClusters)
      .filter({
        account: command.credentials,
        region: command.region,
      })
      .map('name')
      .value();
  }

  public configureAvailableSecrets(command: IEcsServerGroupCommand): void {
    command.backingData.filtered.secrets = chain(command.backingData.secrets)
      .filter({
        account: command.credentials,
        region: command.region,
      })
      .map('name')
      .value();
  }

  public buildServiceRegistryDisplayName(registry: IServiceDiscoveryRegistryDescriptor): string {
    return `${registry.name} (${registry.id})`;
  }

  public mapServiceRegistry(registry: IServiceDiscoveryRegistryDescriptor): IServiceDiscoveryRegistryDescriptor {
    return {
      account: registry.account,
      region: registry.region,
      name: registry.name,
      id: registry.id,
      arn: registry.arn,
      displayName: this.buildServiceRegistryDisplayName(registry),
    };
  }

  public configureAvailableServiceDiscoveryRegistries(command: IEcsServerGroupCommand): void {
    command.backingData.filtered.serviceDiscoveryRegistries = chain(command.backingData.serviceDiscoveryRegistries)
      .filter({
        account: command.credentials,
        region: command.region,
      })
      .map((registry) => this.mapServiceRegistry(registry))
      .value();
  }

  public configureAvailableRegions(command: IEcsServerGroupCommand): void {
    const regionsForAccount: IAccountDetails =
      command.backingData.credentialsKeyedByAccount[command.credentials] || ({ regions: [] } as IAccountDetails);
    command.backingData.filtered.regions = regionsForAccount.regions;
  }

  public configureAvailableIamRoles(command: IEcsServerGroupCommand): void {
    command.backingData.filtered.iamRoles = chain(command.backingData.iamRoles)
      .filter({ accountName: command.credentials })
      .map('name')
      .value();
    if (command.backingData.filtered.iamRoles.length > 0) {
      command.backingData.filtered.iamRoles.splice(0, 0, 'None (No IAM role)');
    }
  }

  public configureSubnetPurposes(command: IEcsServerGroupCommand): IServerGroupCommandResult {
    const result: IEcsServerGroupCommandResult = { dirty: {} };
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

    if (command.subnetTypes) {
      result.dirty.subnetType = !command.subnetTypes.every((subnetType) =>
        chain(filteredData.subnetPurposes).some({ purpose: subnetType }).value(),
      );
    } else if (!chain(filteredData.subnetPurposes).some({ purpose: command.subnetType }).value()) {
      result.dirty.subnetType = true;
    }

    if (result.dirty.subnetType) {
      command.subnetTypes = null;
      command.subnetType = null;
    }

    return result;
  }

  private getLoadBalancerMap(command: IEcsServerGroupCommand): IAmazonLoadBalancer[] {
    return chain(command.backingData.loadBalancers)
      .map('accounts')
      .flattenDeep()
      .filter({ name: command.credentials })
      .map('regions')
      .flattenDeep()
      .filter({ name: command.region })
      .map<IAmazonLoadBalancer>('loadBalancers')
      .flattenDeep<IAmazonLoadBalancer>()
      .value();
  }

  public getLoadBalancerNames(command: IEcsServerGroupCommand): string[] {
    const loadBalancers = this.getLoadBalancerMap(command).filter(
      (lb) => (!lb.loadBalancerType || lb.loadBalancerType === 'classic') && lb.vpcId === command.vpcId,
    );
    return loadBalancers.map((lb) => lb.name).sort();
  }

  public getVpcLoadBalancerNames(command: IEcsServerGroupCommand): string[] {
    const loadBalancersForVpc = this.getLoadBalancerMap(command).filter(
      (lb) => (!lb.loadBalancerType || lb.loadBalancerType === 'classic') && lb.vpcId,
    );
    return loadBalancersForVpc.map((lb) => lb.name).sort();
  }

  public getTargetGroupNames(command: IEcsServerGroupCommand): string[] {
    const loadBalancersV2 = this.getLoadBalancerMap(command).filter((lb) => lb.loadBalancerType !== 'classic') as any[];
    const allTargetGroups = flatten(loadBalancersV2.map<string[]>((lb) => lb.targetGroups));
    return allTargetGroups.sort();
  }

  public configureLoadBalancerOptions(command: IEcsServerGroupCommand): IServerGroupCommandResult {
    const result: IEcsServerGroupCommandResult = { dirty: {} };
    const currentLoadBalancers = (command.loadBalancers || []).concat(command.vpcLoadBalancers || []);
    const newLoadBalancers = this.getLoadBalancerNames(command);
    const vpcLoadBalancers = this.getVpcLoadBalancerNames(command);
    const allTargetGroups = this.getTargetGroupNames(command);
    const currentTargetGroups = command.targetGroupMappings.map((tg) => tg.targetGroup);
    if (currentLoadBalancers && command.loadBalancers) {
      const valid = command.vpcId ? newLoadBalancers : newLoadBalancers.concat(vpcLoadBalancers);
      const matched = intersection(valid, currentLoadBalancers);
      const removedLoadBalancers = xor(matched, currentLoadBalancers);
      command.loadBalancers = intersection(newLoadBalancers, matched);
      if (!command.vpcId) {
        command.vpcLoadBalancers = intersection(vpcLoadBalancers, matched);
      } else {
        delete command.vpcLoadBalancers;
      }
      if (removedLoadBalancers.length) {
        result.dirty.loadBalancers = removedLoadBalancers;
      }
    }

    if (currentTargetGroups) {
      const matched = intersection(allTargetGroups, currentTargetGroups);
      const removedTargetGroups = xor(matched, currentTargetGroups);
      if (removedTargetGroups && removedTargetGroups.length > 0) {
        command.viewState.dirty.targetGroups = removedTargetGroups;
      } else if (command.viewState.dirty && command.viewState.dirty.targetGroups) {
        command.viewState.dirty.targetGroups = [];
      }
    }

    command.backingData.filtered.loadBalancers = newLoadBalancers;
    command.backingData.filtered.vpcLoadBalancers = vpcLoadBalancers;
    command.backingData.filtered.targetGroups = allTargetGroups;
    return result;
  }

  public refreshLoadBalancers(command: IEcsServerGroupCommand, skipCommandReconfiguration?: boolean) {
    return this.loadBalancerReader.listLoadBalancers('ecs').then((loadBalancers) => {
      command.backingData.loadBalancers = loadBalancers;
      if (!skipCommandReconfiguration) {
        this.configureLoadBalancerOptions(command);
      }
    });
  }

  public configureVpcId(command: IEcsServerGroupCommand): IEcsServerGroupCommandResult {
    const result: IEcsServerGroupCommandResult = { dirty: {} };
    if (command.subnetType == null && (command.subnetTypes == null || command.subnetTypes.length == 0)) {
      command.vpcId = null;
      result.dirty.vpcId = true;
    } else if (command.subnetTypes != null && command.subnetTypes.length > 0) {
      command.subnetTypes.forEach(function (subnetType) {
        const subnet = find<ISubnet>(command.backingData.subnets, {
          purpose: subnetType,
          account: command.credentials,
          region: command.region,
        });
        command.vpcId = subnet ? subnet.vpcId : null;
      });
    } else {
      const subnet = find<ISubnet>(command.backingData.subnets, {
        purpose: command.subnetType,
        account: command.credentials,
        region: command.region,
      });
      command.vpcId = subnet ? subnet.vpcId : null;
    }
    return result;
  }

  public checkDirtyCapacityProviders(command: IEcsServerGroupCommand): void {
    if (command.capacityProviderStrategy) {
      const availableCustomCapacityProviders = command.backingData.filtered.availableCapacityProviders;
      const currentCapacityProviders = command.capacityProviderStrategy.map((cp) => cp.capacityProvider);
      const matchedCustomCapacityProviders = intersection(availableCustomCapacityProviders, currentCapacityProviders);
      const removedCustomCapacityProviders = xor(matchedCustomCapacityProviders, currentCapacityProviders);

      if (removedCustomCapacityProviders && removedCustomCapacityProviders.length > 0) {
        command.viewState.dirty.customCapacityProviders = removedCustomCapacityProviders;
      } else if (command.viewState.dirty && command.viewState.dirty.customCapacityProviders) {
        command.viewState.dirty.customCapacityProviders = [];
      }

      if (command.useDefaultCapacityProviders) {
        const availableDefaultCapacityProvider = command.backingData.filtered.defaultCapacityProviderStrategy.map(
          (cp) => cp.capacityProvider,
        );
        const matchedDefaultCapacityProviders = intersection(
          availableDefaultCapacityProvider,
          currentCapacityProviders,
        );
        const removedDefaultCapacityProviders = xor(matchedDefaultCapacityProviders, currentCapacityProviders);

        if (removedDefaultCapacityProviders && removedDefaultCapacityProviders.length > 0) {
          command.viewState.dirty.defaulCapacityProviders = removedDefaultCapacityProviders;
        }
      }
    }
  }

  public attachEventHandlers(cmd: IEcsServerGroupCommand): void {
    cmd.subnetChanged = (command: IEcsServerGroupCommand): IServerGroupCommandResult => {
      const result = this.configureVpcId(command);
      extend(result.dirty, this.configureLoadBalancerOptions(command).dirty);
      command.viewState.dirty = command.viewState.dirty || {};
      extend(command.viewState.dirty, result.dirty);
      return result;
    };

    cmd.regionChanged = (command: IEcsServerGroupCommand): IServerGroupCommandResult => {
      const result: IEcsServerGroupCommandResult = { dirty: {} };
      extend(result.dirty, this.configureSubnetPurposes(command).dirty);
      if (command.region) {
        extend(result.dirty, command.subnetChanged(command).dirty);
        this.configureAvailabilityZones(command);
        this.configureAvailableEcsClusters(command);
        this.configureAvailableSubnetTypes(command);
        this.configureAvailableSecurityGroups(command);
        this.configureAvailableSecrets(command);
        this.configureAvailableServiceDiscoveryRegistries(command);
        this.setCapacityProviderDetails(command);
      }

      return result;
    };

    cmd.subnetTypeChanged = (command: IEcsServerGroupCommand): IServerGroupCommandResult => {
      const result: IEcsServerGroupCommandResult = { dirty: {} };
      this.configureAvailableSecurityGroups(command);
      return result;
    };

    cmd.clusterChanged = (command: IEcsServerGroupCommand): void => {
      command.moniker = NameUtils.getMoniker(command.application, command.stack, command.freeFormDetails);
    };

    cmd.credentialsChanged = (command: IEcsServerGroupCommand): IServerGroupCommandResult => {
      const result: IEcsServerGroupCommandResult = { dirty: {} };
      const backingData = command.backingData;
      if (command.credentials) {
        this.configureAvailableIamRoles(command);
        this.configureAvailableEcsClusters(command);
        this.configureAvailableSubnetTypes(command);
        this.configureAvailableSecurityGroups(command);
        this.configureAvailableSecrets(command);
        this.configureAvailableServiceDiscoveryRegistries(command);
        this.configureAvailableRegions(command);
        this.setCapacityProviderDetails(command);

        if (!some(backingData.filtered.regions, { name: command.region })) {
          command.region = null;
          result.dirty.region = true;
        } else {
          extend(result.dirty, command.regionChanged(command).dirty);
        }
      } else {
        command.region = null;
      }
      return result;
    };

    cmd.placementStrategyNameChanged = (command: IEcsServerGroupCommand): IServerGroupCommandResult => {
      const result: IEcsServerGroupCommandResult = { dirty: {} };
      command.placementStrategySequence = this.placementStrategyService.getPredefinedStrategy(
        command.placementStrategyName,
      );
      return result;
    };

    this.applyOverrides('attachEventHandlers', cmd);
  }
}

export const ECS_SERVER_GROUP_CONFIGURATION_SERVICE = 'spinnaker.ecs.serverGroup.configure.service';
module(ECS_SERVER_GROUP_CONFIGURATION_SERVICE, [
  LOAD_BALANCER_READ_SERVICE,
  SECURITY_GROUP_READER,
  CACHE_INITIALIZER_SERVICE,
  SERVER_GROUP_COMMAND_REGISTRY_PROVIDER,
]).service('ecsServerGroupConfigurationService', EcsServerGroupConfigurationService);
