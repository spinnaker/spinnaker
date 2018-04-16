import { ClusterFilterModel } from 'core/cluster/filter/ClusterFilterModel';
import { ClusterFilterService } from 'core/cluster/filter/ClusterFilterService';
import { ExecutionFilterModel } from 'core/pipeline/filter/ExecutionFilterModel';
import { LoadBalancerFilterModel } from 'core/loadBalancer/filter/LoadBalancerFilterModel';
import { LoadBalancerFilterService } from 'core/loadBalancer/filter/LoadBalancerFilterService';
import { MultiselectModel } from 'core/cluster/filter/MultiselectModel';
import { SecurityGroupFilterModel } from 'core/securityGroup/filter/SecurityGroupFilterModel';
import { SecurityGroupFilterService } from 'core/securityGroup/filter/SecurityGroupFilterService';

export interface IClusterState {
  filterModel: ClusterFilterModel;
  filterService: ClusterFilterService;
  multiselectModel: MultiselectModel;
}

export interface IExecutionState {
  filterModel: ExecutionFilterModel;
}

export interface ILoadBalancersState {
  filterModel: LoadBalancerFilterModel;
  filterService: LoadBalancerFilterService;
}

export interface ISecurityGroupState {
  filterModel: SecurityGroupFilterModel;
  filterService: SecurityGroupFilterService;
}

export const ClusterState: IClusterState = {} as any;
export const ExecutionState: IExecutionState = {} as any;
export const LoadBalancerState: ILoadBalancersState = {} as any;
export const SecurityGroupState: ISecurityGroupState = {} as any;

export function initialize(): void {
  ClusterState.filterModel = new ClusterFilterModel();
  ClusterState.filterService = new ClusterFilterService();
  ClusterState.multiselectModel = new MultiselectModel();

  ExecutionState.filterModel = new ExecutionFilterModel();

  LoadBalancerState.filterModel = new LoadBalancerFilterModel();
  LoadBalancerState.filterService = new LoadBalancerFilterService();

  SecurityGroupState.filterModel = new SecurityGroupFilterModel();
  SecurityGroupState.filterService = new SecurityGroupFilterService();
}
