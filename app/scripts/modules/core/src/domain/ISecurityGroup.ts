import { ILoadBalancer } from 'core/domain/ILoadBalancer';
import { IServerGroup } from 'core/domain/IServerGroup';
import { IEntityTags } from './IEntityTags';

export interface ILoadBalancerUsage {
  name: string;
}

export interface IServerGroupUsage {
  name: string;
  isDisabled: boolean;
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
  securityGroup?: ISecurityGroup;
  serverGroups?: IServerGroup[];
  loadBalancers?: ILoadBalancer[];
  subgroups?: ISecurityGroupGroup[];
  searchField?: string;
  vpcName?: string;
}
