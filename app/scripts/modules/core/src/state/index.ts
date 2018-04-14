import { ClusterFilterModel } from 'core/cluster/filter/ClusterFilterModel';
import { ExecutionFilterModel } from 'core/pipeline/filter/ExecutionFilterModel';
import { LoadBalancerFilterModel } from 'core/loadBalancer/filter/LoadBalancerFilterModel';
import { MultiselectModel } from 'core/cluster/filter/MultiselectModel';

export interface IClusterState {
  filterModel: ClusterFilterModel;
  multiselectModel: MultiselectModel;
}

export interface IExecutionState {
  filterModel: ExecutionFilterModel;
}

export interface ILoadBalancersState {
  filterModel: LoadBalancerFilterModel;
}

export const ClusterState: IClusterState = {} as any;
export const ExecutionState: IExecutionState = {} as any;
export const LoadBalancerState: ILoadBalancersState = {} as any;

export function initialize(): void {
  ClusterState.filterModel = new ClusterFilterModel();
  ClusterState.multiselectModel = new MultiselectModel();
  ExecutionState.filterModel = new ExecutionFilterModel();
  LoadBalancerState.filterModel = new LoadBalancerFilterModel();
}
