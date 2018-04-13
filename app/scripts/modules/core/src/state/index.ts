import { LoadBalancerFilterModel } from 'core/loadBalancer/filter/LoadBalancerFilterModel';

export interface ILoadBalancersState {
  filterModel: LoadBalancerFilterModel;
}

export const LoadBalancerState: ILoadBalancersState = {} as any;

export function initialize(): void {
  LoadBalancerState.filterModel = new LoadBalancerFilterModel();
}
