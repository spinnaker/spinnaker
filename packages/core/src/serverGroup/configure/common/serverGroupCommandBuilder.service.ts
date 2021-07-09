import { module } from 'angular';
import { IAggregatedAccounts, IRegion } from '../../../account/AccountService';
import { Application } from '../../../application/application.model';
import { PROVIDER_SERVICE_DELEGATE, ProviderServiceDelegate } from '../../../cloudProvider';
import { IDeploymentStrategy } from '../../../deploymentStrategy';
import { ILoadBalancer, IManagedResourceSummary, IPipeline, ISecurityGroup, IStage, ISubnet } from '../../../domain';
import { IPreferredInstanceType } from '../../../instance';
import { getKindName } from '../../../managed';
import { IMoniker } from '../../../naming/IMoniker';
import { ISecurityGroupsByAccountSourceData } from '../../../securityGroup/securityGroupReader.service';

import { ICapacity } from '../../serverGroupWriter.service';

export interface IServerGroupCommandBuilderOptions {
  account: string;
  mode: string;
  region: string;
}

export interface IServerGroupCommandDirty {
  availabilityZones?: boolean;
  amiName?: boolean;
  instanceType?: string;
  keyPair?: boolean;
  loadBalancers?: string[];
  region?: boolean;
  securityGroups?: string[];
  subnetType?: boolean;
  vpcId?: boolean;
}

export interface IServerGroupCommandResult {
  dirty?: IServerGroupCommandDirty;
}

export interface IServerGroupCommandViewState {
  customTemplateMessage: string;
  dirty?: IServerGroupCommandDirty;
  disableImageSelection: boolean;
  expectedArtifacts: any[];
  imageId?: string;
  instanceProfile: string;
  instanceTypeDetails?: IPreferredInstanceType;
  disableNoTemplateSelection?: boolean;
  disableStrategySelection?: boolean;
  hideClusterNamePreview?: boolean;
  imageSourceText?: string;
  overrides: any;
  overriddenStorageDescription?: string;
  readOnlyFields: { [field: string]: boolean };
  requiresTemplateSelection?: boolean;
  submitButtonLabel?: string;
  showImageSourceSelector: true;
  useAllImageSelection: boolean;
  useSimpleCapacity: boolean;
  usePreferredZones: boolean;
  mode: string;
  pipeline?: IPipeline;
  stage?: IStage;
}

export interface IServerGroupCommandBackingDataFiltered {
  availabilityZones: string[];
  images: any[];
  instanceTypes: string[];
  loadBalancers: string[];
  regionalAvailabilityZones: string[];
  regions: IRegion[];
  securityGroups: ISecurityGroup[];
  subnetPurposes: ISubnet[];
  vpcLoadBalancers: string[];
}

export interface IServerGroupCommandBackingData {
  credentialsKeyedByAccount: IAggregatedAccounts;
  enabledMetrics: string[];
  healthCheckTypes: string[];
  instanceTypes: string[];
  managedResources: IManagedResourceSummary[];
  loadBalancers: ILoadBalancer[];
  terminationPolicies: string[];
  subnets: ISubnet[];
  accounts: string[];
  filtered: IServerGroupCommandBackingDataFiltered;
  packageImages: any[];
  preferredZones: {
    [credentials: string]: {
      [region: string]: string[];
    };
  };
  securityGroups: ISecurityGroupsByAccountSourceData;
}

export interface IServerGroupCommand {
  amiName?: string;
  application: string;
  availabilityZones: string[];
  backingData: IServerGroupCommandBackingData;
  capacity: ICapacity;
  cloudProvider: string;
  cooldown: number;
  credentials: string;
  disableNoTemplateSelection?: boolean;
  enabledMetrics: string[];
  freeFormDetails?: string;
  healthCheckType: string;
  iamRole: string;
  imageArtifactId?: string;
  instanceType: string;
  interestingHealthProviderNames: string[];
  loadBalancers: string[];
  vpcLoadBalancers: string[];
  preferSourceCapacity?: boolean;
  reason?: string;
  region: string;
  resourceSummary?: IManagedResourceSummary;
  securityGroups: string[];
  selectedProvider: string;
  source?: {
    asgName: string;
  };
  stack?: string;
  moniker?: IMoniker;
  strategy: string;
  subnetType: string;
  suspendedProcesses: string[];
  tags: { [key: string]: string };
  terminationPolicies: string[];
  type?: string;
  useSourceCapacity?: boolean;
  viewState: IServerGroupCommandViewState;
  virtualizationType: string;
  vpcId: string;

  processIsSuspended: (command: IServerGroupCommand, process: string) => boolean;
  toggleSuspendedProcess: (command: IServerGroupCommand, process: string) => void;
  onStrategyChange: (command: IServerGroupCommand, strategy: IDeploymentStrategy) => void;
  subnetChanged: (command: IServerGroupCommand) => IServerGroupCommandResult;
  regionChanged: (command: IServerGroupCommand) => IServerGroupCommandResult;
  credentialsChanged: (command: IServerGroupCommand) => IServerGroupCommandResult;
  imageChanged: (command: IServerGroupCommand) => IServerGroupCommandResult;
  instanceTypeChanged: (command: IServerGroupCommand) => void;
  clusterChanged?: (command: IServerGroupCommand) => void;
}

export const setMatchingResourceSummary = (command: IServerGroupCommand) => {
  command.resourceSummary = (command.backingData.managedResources ?? []).find(
    (resource) =>
      !resource.isPaused &&
      getKindName(resource.kind) === 'cluster' &&
      resource.locations.regions.some((r) => r.name === command.region) &&
      (resource.moniker.stack ?? '') === command.stack &&
      (resource.moniker.detail ?? '') === command.freeFormDetails &&
      resource.locations.account === command.credentials,
  );
};

export class ServerGroupCommandBuilderService {
  private getDelegate(provider: string): any {
    return this.providerServiceDelegate.getDelegate(provider, 'serverGroup.commandBuilder');
  }

  public static $inject = ['providerServiceDelegate'];
  constructor(private providerServiceDelegate: ProviderServiceDelegate) {}

  public buildNewServerGroupCommand(
    application: Application,
    provider: string,
    options: IServerGroupCommandBuilderOptions,
  ): any {
    return this.getDelegate(provider).buildNewServerGroupCommand(application, options);
  }

  public buildServerGroupCommandFromExisting(application: Application, serverGroup: any, mode?: string): any {
    return this.getDelegate(serverGroup.type).buildServerGroupCommandFromExisting(application, serverGroup, mode);
  }

  public buildNewServerGroupCommandForPipeline(provider: string, currentStage: any, pipeline: any): any {
    return this.getDelegate(provider).buildNewServerGroupCommandForPipeline(currentStage, pipeline);
  }

  public buildServerGroupCommandFromPipeline(
    application: Application,
    cluster: any,
    currentStage: any,
    pipeline: any,
  ): any {
    return this.getDelegate(cluster.provider).buildServerGroupCommandFromPipeline(
      application,
      cluster,
      currentStage,
      pipeline,
    );
  }
}

export const SERVER_GROUP_COMMAND_BUILDER_SERVICE = 'spinnaker.core.serverGroup.configure.common.service';
module(SERVER_GROUP_COMMAND_BUILDER_SERVICE, [PROVIDER_SERVICE_DELEGATE]).service(
  'serverGroupCommandBuilder',
  ServerGroupCommandBuilderService,
);
