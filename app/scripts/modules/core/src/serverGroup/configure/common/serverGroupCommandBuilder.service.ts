import {module} from 'angular';

import { Application } from 'core/application/application.model';
import { ILoadBalancer, ISecurityGroup, ISubnet, IEntityTags } from 'core/domain';
import { ICapacity } from 'core/serverGroup/serverGroupWriter.service';
import { IDeploymentStrategy } from 'core/deploymentStrategy';
import { IGroupsByAccount } from 'core/securityGroup/securityGroupReader.service';
import { IRegion, IAggregatedAccounts } from 'core/account/account.service';

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
  securityGroups?: ISecurityGroup[];
  subnetType?: boolean;
  vpcId?: boolean;
}

export interface IServerGroupCommandResult {
  dirty?: IServerGroupCommandDirty;
}

export interface IServerGroupCommandViewState {
  dirty?: IServerGroupCommandDirty;
  disableImageSelection: boolean;
  imageId?: string;
  instanceProfile: string;
  useAllImageSelection: boolean;
  useSimpleCapacity: boolean;
  usePreferredZones: boolean;
  mode: string;
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
  loadBalancers: ILoadBalancer[];
  terminationPolicies: string[];
  subnets: ISubnet[];
  accounts: string[];
  filtered: IServerGroupCommandBackingDataFiltered;
  packageImages: any[];
  preferredZones: {
    [credentials: string]: {
      [region: string]: string[]
    }
  }
  scalingProcesses: string[];
  securityGroups: IGroupsByAccount;
}

export interface IServerGroupCommand extends IServerGroupCommandResult {
  amiName?: string;
  application: Application;
  availabilityZones: string[];
  backingData: IServerGroupCommandBackingData;
  capacity: ICapacity;
  cooldown: number;
  credentials: string;
  enabledMetrics: string[];
  freeFormDetails?: string;
  healthCheckType: string;
  iamRole: string;
  instanceType: string;
  loadBalancers: string[];
  vpcLoadBalancers: string[];
  region: string;
  securityGroups: string[];
  selectedProvider: string;
  source?: {
    asgName: string;
  };
  stack?: string;
  strategy: string;
  subnetType: string;
  suspendedProcesses: string[];
  tags: IEntityTags;
  terminationPolicies: string[];
  type?: string;
  viewState: IServerGroupCommandViewState;
  virtualizationType: string;
  vpcId: string;

  processIsSuspended: (process: string) => boolean;
  toggleSuspendedProcess: (process: string) => void;
  onStrategyChange: (strategy: IDeploymentStrategy) => void;
  regionIsDeprecated: () => boolean;
  subnetChanged: () => IServerGroupCommandResult;
  regionChanged: () => IServerGroupCommandResult;
  credentialsChanged: () => IServerGroupCommandResult;
  imageChanged: () => IServerGroupCommandResult;
}

export class ServerGroupCommandBuilderService {

  private getDelegate(provider: string): any {
    return this.serviceDelegate.getDelegate(provider, 'serverGroup.commandBuilder');
  }

  constructor(private serviceDelegate: any) { 'ngInject'; }

  public buildNewServerGroupCommand(application: Application,
                                    provider: string,
                                    options: IServerGroupCommandBuilderOptions): any {
    return this.getDelegate(provider).buildNewServerGroupCommand(application, options);
  }

  public buildServerGroupCommandFromExisting(application: Application,
                                             serverGroup: any,
                                             mode: string): any {
    return this.getDelegate(serverGroup.type).buildServerGroupCommandFromExisting(application, serverGroup, mode);
  }

  public buildNewServerGroupCommandForPipeline(provider: string,
                                               currentStage: any,
                                               pipeline: any): any {
    return this.getDelegate(provider).buildNewServerGroupCommandForPipeline(currentStage, pipeline);
  }

  public buildServerGroupCommandFromPipeline(application: Application,
                                             cluster: any,
                                             currentStage: any,
                                             pipeline: any): any {
    return this.getDelegate(cluster.provider).buildServerGroupCommandFromPipeline(application, cluster, currentStage, pipeline);
  }
}

export const SERVER_GROUP_COMMAND_BUILDER_SERVICE = 'spinnaker.core.serverGroup.configure.common.service';
module(SERVER_GROUP_COMMAND_BUILDER_SERVICE, [
  require('core/cloudProvider/serviceDelegate.service.js')
])
  .service('serverGroupCommandBuilder', ServerGroupCommandBuilderService);
