import { IInstance } from './IInstance';
import { IInstanceCounts } from './IInstanceCounts';
import { IServerGroup } from './IServerGroup';
import { ITaggedEntity } from './ITaggedEntity';
import { IMoniker } from 'core/naming/IMoniker';

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
  listenerDescriptions?: any[];
  loadBalancerType?: string;
  name?: string;
  moniker?: IMoniker;
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
