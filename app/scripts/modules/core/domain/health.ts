import { ILoadBalancer } from './loadBalancer';

export class Health {
  type: string;
  loadBalancers: ILoadBalancer[];
}
