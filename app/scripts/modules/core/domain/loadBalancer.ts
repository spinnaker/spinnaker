import { InstanceCounts } from './instanceCounts';
import { Instance } from './instance';

export interface ILoadBalancer {
  name: string;
  account?: string;
  region?: string;
  cloudProvider: string;
  type?: string;
  provider?: string;
  vpcId?: string;
  instances?: Instance[];
  instanceCounts?: InstanceCounts;
  healthState?: string;
  loadBalancerType?: string;
  serverGroups?: any[];
  stack?: string;
  detail?: string;
}
