import { ILoadBalancer } from 'core/domain/ILoadBalancer';
import { IServerGroup } from 'core/domain/IServerGroup';
import { IMoniker } from 'core/naming';
import { IManagedResourceSummary } from 'core/managed';

import { IEntityTags } from './IEntityTags';

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

export interface ISecurityGroup {
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
  isManaged?: boolean;
  moniker?: IMoniker;
  managedResourceSummary?: IManagedResourceSummary;
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

export interface ISecurityGroupGroup {
  heading: string;
  isManaged?: boolean;
  loadBalancers?: ILoadBalancer[];
  managedResourceSummary?: IManagedResourceSummary;
  searchField?: string;
  securityGroup?: ISecurityGroup;
  serverGroups?: IServerGroup[];
  subgroups?: ISecurityGroupGroup[];
  vpcName?: string;
}
