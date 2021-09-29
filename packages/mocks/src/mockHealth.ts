import type { IHealth, ILoadBalancerHealth } from '@spinnaker/core';

export const mockLoadBalancerHealth: ILoadBalancerHealth = {
  name: 'load-b',
  state: 'Up',
  description: 'alb',
  healthState: 'Up',
};

export const mockHealth: IHealth = {
  type: 'Discovery',
  state: 'Up',
  loadBalancers: [mockLoadBalancerHealth],
};
