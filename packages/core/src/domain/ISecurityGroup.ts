import { IEntityTags } from './IEntityTags';
import { ILoadBalancer } from './ILoadBalancer';
import { IManagedResource } from './IManagedEntity';
import { IServerGroup } from './IServerGroup';
import { IMoniker } from '../naming';

export interface ILoadBalancerUsage {
  name: string;
}

export interface IServerGroupUsage {
  account: string;
  cloudProvider: string;
  isDisabled: boolean;
  name: string;
  region: string;
}

export interface IUsages {
  loadBalancers: ILoadBalancerUsage[];
  serverGroups: IServerGroupUsage[];
}

export interface ISecurityGroup extends IManagedResource {
  account?: string;
  accountId?: string;
  accountName?: string;
  application?: string;
  cloudProvider?: string;
  credentials?: string;
  detail?: string;
  entityTags?: IEntityTags;
  id?: string;
  inferredName?: boolean;
  moniker?: IMoniker;
  name?: string;
  provider?: string;
  region?: string;
  searchField?: string;
  stack?: string;
  type?: string;
  usages?: IUsages;
  vpcId?: string;
  vpcName?: string;
}

export interface ISecurityGroupGroup extends IManagedResource {
  heading: string;
  loadBalancers?: ILoadBalancer[];
  searchField?: string;
  securityGroup?: ISecurityGroup;
  serverGroups?: IServerGroup[];
  subgroups?: ISecurityGroupGroup[];
  vpcName?: string;
}
