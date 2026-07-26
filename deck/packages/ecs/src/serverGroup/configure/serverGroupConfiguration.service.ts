import type { IArtifact, IPipeline, IServerGroupCommandResult, IStage } from '@spinnaker/core';
import type { IDockerImage } from '@spinnaker/docker';

import type { IEcsCapacityProviderDetails } from '../../ecsCluster/IEcsCapacityProviderDetails';
import type { IEcsClusterDescriptor } from '../../ecsCluster/IEcsCluster';
import type { IRoleDescriptor } from '../../iamRoles/IRole';
import type { IMetricAlarmDescriptor } from '../../metricAlarm/MetricAlarm';
import type { IPlacementStrategy } from '../../placementStrategy/IPlacementStrategy';
import type { ISecretDescriptor } from '../../secrets/ISecret';
import type { IServiceDiscoveryRegistryDescriptor } from '../../serviceDiscovery/IServiceDiscovery';

export interface IEcsServerGroupCommandDirty {
  [key: string]: any;
}

export interface IEcsServerGroupCommandResult extends IServerGroupCommandResult {
  dirty: IEcsServerGroupCommandDirty;
}

export interface IEcsDockerImage extends IDockerImage {
  [key: string]: any;
  imageId: string;
  message: string;
  fromTrigger: boolean;
  fromContext: boolean;
  stageId: string;
  imageLabelOrSha: string;
}

export interface IEcsServerGroupCommandViewState {
  [key: string]: any;
  contextImages: IEcsDockerImage[];
  pipeline: IPipeline;
  currentStage: IStage;
  dirty: IEcsServerGroupCommandDirty;
}

export interface IEcsServerGroupCommandBackingDataFiltered {
  [key: string]: any;
  targetGroups: string[];
  iamRoles: string[];
  ecsClusters: string[];
  availableCapacityProviders: string[];
  defaultCapacityProviderStrategy: IEcsCapacityProviderStrategyItem[];
  metricAlarms: IMetricAlarmDescriptor[];
  subnetTypes: any[];
  securityGroupNames: string[];
  secrets: string[];
  serviceDiscoveryRegistries: IServiceDiscoveryRegistryDescriptor[];
  images: IEcsDockerImage[];
}

export interface IEcsServerGroupCommandBackingData {
  [key: string]: any;
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

export interface IEcsServerGroupCommand {
  [key: string]: any;
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
}

export class EcsServerGroupConfigurationService {
  public configureUpdateCommand(command: IEcsServerGroupCommand): void {
    command.backingData = {
      filtered: {} as IEcsServerGroupCommandBackingDataFiltered,
      launchTypes: ['EC2', 'FARGATE'],
      networkModes: ['bridge', 'host', 'awsvpc', 'none', 'default'],
    } as IEcsServerGroupCommandBackingData;
  }
}
