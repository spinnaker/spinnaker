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

export interface IEcsMetricConfiguration {
  metricNames: string[];
  resolutionSeconds: number;
}

export interface IEcsMonitoringConfiguration {
  metricConfigurations: IEcsMetricConfiguration[];
}

export interface IEcsServerGroupCommand {
  [key: string]: any;
  copySourceMonitoringConfiguration?: boolean;
  monitoringConfiguration?: IEcsMonitoringConfiguration;
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
  /**
   * 'ecs' (default) or 'ecs-native', toggled by the "Use native ECS deployment" checkbox on the
   * Native ECS Deployment wizard page. See EcsServerGroupTransformer.
   */
  cloudProvider?: string;
  strategy?: string;
  deploymentCircuitBreakerRollback?: boolean;
  alarmNames?: string[];
  enableDeploymentAlarms?: boolean;
  deploymentAlarmsRollback?: boolean;
  deploymentStrategy?: string;
  minimumHealthyPercent?: number;
  maximumPercent?: number;
  bakeTimeInMinutes?: number;
  lifecyclePauseStage?: string;
  lifecyclePauseTimeoutMinutes?: number;
  lifecyclePauseTimeoutAction?: string;
  alternateTargetGroupArn?: string;
  productionListenerRule?: string;
  testListenerRule?: string;
  blueGreenRoleArn?: string;
}

/**
 * Fields that only mean something to the ecs-native provider. They are removed from the command
 * when "Use native ECS deployment" is unticked (and stripped again when a classic `ecs` command is
 * serialized) so a classic deploy never carries a native-only payload. `enableDeploymentCircuitBreaker`
 * and `useSourceCapacity` are not listed: classic ECS supports both.
 */
export const ECS_NATIVE_ONLY_FIELDS = [
  'deploymentStrategy',
  'deploymentCircuitBreakerRollback',
  'alarmNames',
  'enableDeploymentAlarms',
  'deploymentAlarmsRollback',
  'minimumHealthyPercent',
  'maximumPercent',
  'bakeTimeInMinutes',
  'lifecyclePauseStage',
  'lifecyclePauseTimeoutMinutes',
  'lifecyclePauseTimeoutAction',
  'alternateTargetGroupArn',
  'productionListenerRule',
  'testListenerRule',
  'blueGreenRoleArn',
] as const;

/** The native fields the wizard only renders for the BLUE_GREEN strategy. */
export const ECS_NATIVE_BLUE_GREEN_ONLY_FIELDS = [
  'lifecyclePauseStage',
  'lifecyclePauseTimeoutMinutes',
  'lifecyclePauseTimeoutAction',
  'alternateTargetGroupArn',
  'productionListenerRule',
  'testListenerRule',
  'blueGreenRoleArn',
] as const;

/** Matches clouddriver, which compares the native strategy case-insensitively. */
export const isEcsNativeBlueGreenStrategy = (strategy: unknown): boolean =>
  typeof strategy === 'string' && strategy.trim().toUpperCase() === 'BLUE_GREEN';

/**
 * A strategy that is neither BLUE_GREEN nor something we can judge in Deck: an omitted value lets
 * clouddriver keep the service's stored strategy on redeploy, and a SpEL expression is only known
 * at runtime. Blue/green-only fields must survive both.
 */
export const isEcsNativeStrategyDecidedInDeck = (strategy: unknown): boolean =>
  typeof strategy === 'string' && strategy.trim() !== '' && !strategy.includes('${');

export class EcsServerGroupConfigurationService {
  public configureUpdateCommand(command: IEcsServerGroupCommand): void {
    command.backingData = {
      filtered: {} as IEcsServerGroupCommandBackingDataFiltered,
      launchTypes: ['EC2', 'FARGATE'],
      networkModes: ['bridge', 'host', 'awsvpc', 'none', 'default'],
    } as IEcsServerGroupCommandBackingData;
  }
}
