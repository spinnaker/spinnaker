export interface IHealth {
  description: string;
  healthCheckUrl?: string;
  loadBalancers: ILoadBalancerHealth[];
  state: string;
  statusPageUrl?: string;
  type: string;
}

export interface ILoadBalancerHealth {
  name: string;
  state: string;
  description: string;
  healthState: string;
}
