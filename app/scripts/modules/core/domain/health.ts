import { ILoadBalancer } from './loadBalancer';

export class Health {
  public type: string;
  public state: string;
  public loadBalancers: ILoadBalancer[];
}
