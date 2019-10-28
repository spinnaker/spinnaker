import { IMoniker } from 'core/naming';
import { IManagedResourceSummary } from 'core/managed';

import { IInstance } from './IInstance';
import { IInstanceCounts } from './IInstanceCounts';
import { IServerGroup } from './IServerGroup';
import { ITaggedEntity } from './ITaggedEntity';

export interface ILoadBalancerSourceData {
  cloudProvider?: string;
  name?: string;
  provider?: string;
  type?: string;
}

export interface ILoadBalancer extends ITaggedEntity {
  account?: string;
  cloudProvider?: string;
  detail?: string;
  healthState?: string;
  instanceCounts?: IInstanceCounts;
  instances?: IInstance[];
  isManaged?: boolean;
  listenerDescriptions?: any[];
  loadBalancerType?: string;
  managedResourceSummary?: IManagedResourceSummary;
  moniker?: IMoniker;
  name?: string;
  provider?: string;
  region?: string;
  searchField?: string;
  securityGroups?: string[];
  serverGroups?: IServerGroup[];
  stack?: string;
  type?: string;
  vpcId?: string;
  vpcName?: string;
}

export interface ILoadBalancerGroup {
  heading: string;
  loadBalancer?: ILoadBalancer;
  serverGroups?: IServerGroup[];
  subgroups?: ILoadBalancerGroup[];
  searchField?: string;
}
