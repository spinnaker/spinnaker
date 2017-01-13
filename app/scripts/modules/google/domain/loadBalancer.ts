import {ILoadBalancer} from 'core/domain/loadBalancer';
export interface IGceLoadBalancer extends ILoadBalancer {
  stack: string;
  detail: string;
  loadBalancerName: string;
  credentials: string;
  account: string;
  region: string;
}
