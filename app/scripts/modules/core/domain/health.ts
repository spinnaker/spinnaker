import { ILoadBalancer } from './loadBalancer';

export class Health {
  public type: string;
  public loadBalancers: ILoadBalancer[];
}
