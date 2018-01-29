export interface IHealth {
  type: string;
  state: string;
  loadBalancers: ILoadBalancerHealth[];
}

export interface ILoadBalancerHealth {
  name: string;
  state: string;
  description: string;
  healthState: string;
}
