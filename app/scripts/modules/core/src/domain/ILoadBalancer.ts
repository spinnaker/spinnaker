import { ITaggedEntity } from './ITaggedEntity';
import { IServerGroup } from './IServerGroup';
import { IInstanceCounts } from './IInstanceCounts';
import { IInstance } from './IInstance';

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
  serverGroups?: any[];
  stack?: string;
  type?: string;
  vpcId?: string;
  vpcName?: string;
  searchField?: string;
  listenerDescriptions?: any[];
}

export interface ILoadBalancerGroup {
  heading: string;
  loadBalancer?: ILoadBalancer;
  serverGroups?: IServerGroup[];
  subgroups?: ILoadBalancerGroup[];
  searchField?: string;
}
