import { ClusterFilterModel } from 'core/cluster/filter/ClusterFilterModel';
import { ClusterFilterService } from 'core/cluster/filter/ClusterFilterService';
import { SETTINGS } from 'core/config/settings';
import { VersionChecker } from 'core/config/VersionChecker';
import { ExecutionFilterModel } from 'core/pipeline/filter/ExecutionFilterModel';
import { LoadBalancerFilterModel } from 'core/loadBalancer/filter/LoadBalancerFilterModel';
import { LoadBalancerFilterService } from 'core/loadBalancer/filter/LoadBalancerFilterService';
import { MultiselectModel } from 'core/cluster/filter/MultiselectModel';
import { SecurityGroupFilterModel } from 'core/securityGroup/filter/SecurityGroupFilterModel';
import { SecurityGroupFilterService } from 'core/securityGroup/filter/SecurityGroupFilterService';
import { FunctionFilterModel } from 'core/function/filter/FunctionFilterModel';
import { FunctionFilterService } from 'core/function/filter/FunctionFilterService';

export interface IStateCluster {
  filterModel: ClusterFilterModel;
  filterService: ClusterFilterService;
  multiselectModel: MultiselectModel;
}

export interface IStateExecution {
  filterModel: ExecutionFilterModel;
}

export interface IStateLoadBalancers {
  filterModel: LoadBalancerFilterModel;
  filterService: LoadBalancerFilterService;
}

export interface IStateSecurityGroup {
  filterModel: SecurityGroupFilterModel;
  filterService: SecurityGroupFilterService;
}

export interface IStateFunctions {
  filterModel: FunctionFilterModel;
  filterService: FunctionFilterService;
}

export const ClusterState = {} as IStateCluster;
export const ExecutionState = {} as IStateExecution;
export const LoadBalancerState = {} as IStateLoadBalancers;
export const SecurityGroupState = {} as IStateSecurityGroup;
export const FunctionState = {} as IStateFunctions;

export function initialize(): void {
  ClusterState.filterModel = new ClusterFilterModel();
  ClusterState.filterService = new ClusterFilterService();
  ClusterState.multiselectModel = new MultiselectModel();

  ExecutionState.filterModel = new ExecutionFilterModel();

  LoadBalancerState.filterModel = new LoadBalancerFilterModel();
  LoadBalancerState.filterService = new LoadBalancerFilterService();

  FunctionState.filterModel = new FunctionFilterModel();
  FunctionState.filterService = new FunctionFilterService();

  SecurityGroupState.filterModel = new SecurityGroupFilterModel();
  SecurityGroupState.filterService = new SecurityGroupFilterService();
  if (SETTINGS.checkForUpdates) {
    VersionChecker.initialize();
  }
}
