import type {
  IInstance,
  ILoadBalancer,
  IManifest,
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
  publicDnsName?: string;
}

export interface IKubernetesLoadBalancer extends ILoadBalancer, IKubernetesResource {}

export interface IKubernetesSecurityGroup extends ISecurityGroupDetail, IKubernetesResource {
  account: string;
  moniker: IMoniker;
}

export interface IKubernetesServerGroup extends IServerGroup, IKubernetesResource {
  disabled: boolean;
}

export interface IKubernetesServerGroupView extends IKubernetesServerGroup {
  manifest: IManifest;
}

export interface IKubernetesServerGroupManager extends IServerGroupManager, IKubernetesResource {}
