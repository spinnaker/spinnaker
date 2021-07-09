import { IInstance } from './IInstance';
import { IInstanceCounts } from './IInstanceCounts';
import { IManagedResource } from './IManagedEntity';
import { IServerGroup } from './IServerGroup';
import { ITaggedEntity } from './ITaggedEntity';
import { IMoniker } from '../naming';

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
