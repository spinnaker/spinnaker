import { ITaggedEntity } from './ITaggedEntity';
import { ServerGroup } from './serverGroup';
import { InstanceCounts } from './instanceCounts';
import { Instance } from './instance';

export interface ILoadBalancer extends ITaggedEntity {
  account?: string;
  cloudProvider?: string;
  name?: string;
  detail?: string;
  healthState?: string;
  instances?: Instance[];
  instanceCounts?: InstanceCounts;
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
}

export interface ILoadBalancerGroup {
  heading: string;
  loadBalancer?: ILoadBalancer;
  serverGroups?: ServerGroup[];
  subgroups?: ILoadBalancerGroup[];
  searchField?: string;
}
