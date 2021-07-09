import {
  IInstance,
  ILoadBalancer,
  IMoniker,
  ISecurityGroupDetail,
  IServerGroup,
  IServerGroupManager,
} from '@spinnaker/core';

export interface IKubernetesResource {
  apiVersion: string;
  createdTime?: number;
  displayName: string;
  kind: string;
  namespace: string;
}

export interface IKubernetesInstance extends IInstance, IKubernetesResource {
  humanReadableName: string;
  moniker: IMoniker;
}

export interface IKubernetesLoadBalancer extends ILoadBalancer, IKubernetesResource {}

export interface IKubernetesSecurityGroup extends ISecurityGroupDetail, IKubernetesResource {
  account: string;
  moniker: IMoniker;
}

export interface IKubernetesServerGroup extends IServerGroup, IKubernetesResource {
  disabled: boolean;
}

export interface IKubernetesServerGroupManager extends IServerGroupManager, IKubernetesResource {}
