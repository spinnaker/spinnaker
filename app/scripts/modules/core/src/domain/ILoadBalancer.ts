import { ITaggedEntity } from './ITaggedEntity';
import { IServerGroup } from './IServerGroup';
import { IInstanceCounts } from './IInstanceCounts';
import { IInstance } from './IInstance';

export interface ILoadBalancerSourceData {
  cloudProvider?: string;
  name?: string;
  provider?: string;
  type?: string;
}

export interface ILoadBalancer extends ITaggedEntity {
  account?: string;
  cloudProvider?: string;
  name?: string;
  detail?: string;
  healthState?: string;
  instances?: IInstance[];
  instanceCounts?: IInstanceCounts;
  loadBalancerType?: string;
  provider?: string;
  region?: string;
  securityGroups?: string[];
  serverGroups?: IServerGroup[];
  stack?: string;
  vpcId?: string;
  vpcName?: string;
  searchField?: string;
  type?: string;
  listenerDescriptions?: any[];
}

export interface ILoadBalancerGroup {
  heading: string;
  loadBalancer?: ILoadBalancer;
  serverGroups?: IServerGroup[];
  subgroups?: ILoadBalancerGroup[];
  searchField?: string;
}

export interface IUpsertLoadBalancerCommand {
  credentials: string;
  detail?: string;
  name: string;
  region: string;
  stack?: string;
}
