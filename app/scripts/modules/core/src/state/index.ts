import { ClusterFilterModel } from 'core/cluster/filter/clusterFilter.model';
import { LoadBalancerFilterModel } from 'core/loadBalancer/filter/LoadBalancerFilterModel';

export interface IClusterState {
  filterModel: ClusterFilterModel;
}

export interface ILoadBalancersState {
  filterModel: LoadBalancerFilterModel;
}

export const ClusterState: IClusterState = {} as any;
export const LoadBalancerState: ILoadBalancersState = {} as any;

export function initialize(): void {
  ClusterState.filterModel = new ClusterFilterModel();
  LoadBalancerState.filterModel = new LoadBalancerFilterModel();
}
