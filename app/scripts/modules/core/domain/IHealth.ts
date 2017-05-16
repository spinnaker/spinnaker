import { ILoadBalancer } from './ILoadBalancer';

export interface IHealth {
  type: string;
  state: string;
  loadBalancers: ILoadBalancer[];
}
