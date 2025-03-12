import type { IInstance } from './IInstance';
import type { IInstanceCounts } from './IInstanceCounts';
import type { IManagedResource } from './IManagedEntity';
import type { IServerGroup } from './IServerGroup';
import type { ITaggedEntity } from './ITaggedEntity';
import type { IMoniker } from '../naming';

export interface ILoadBalancerSourceData {
  cloudProvider?: string;
  name?: string;
  provider?: string;
  type?: string;
}

export interface ILoadBalancer extends ITaggedEntity, IManagedResource {
  account?: string;
  cloudProvider?: string;
  detail?: string;
  healthState?: string;
  instanceCounts?: IInstanceCounts;
  instances?: IInstance[];
  listenerDescriptions?: any[];
  loadBalancerType?: string;
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

export interface ILoadBalancerGroup extends IManagedResource {
  heading: string;
  loadBalancer?: ILoadBalancer;
  serverGroups?: IServerGroup[];
  subgroups?: ILoadBalancerGroup[];
  searchField?: string;
}
