import type { IInstance, ILoadBalancer, IMoniker, IServerGroup } from '@spinnaker/core';

export interface ICloudrunResource {
  apiVersion: string;
  createdTime?: number;
  displayName: string;
  kind: string;
  namespace: string;
}

export interface ICloudrunInstance extends IInstance, ICloudrunResource {
  humanReadableName: string;
  moniker: IMoniker;
  publicDnsName?: string;
}

export interface ICloudrunLoadBalancer extends ILoadBalancer, ICloudrunResource {}

export interface ICloudrunServerGroup extends IServerGroup, ICloudrunResource {
  disabled: boolean;
}

//export interface ICloudrunServerGroupManager extends IServerGroupManager, ICloudrunResource {}
