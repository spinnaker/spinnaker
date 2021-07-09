import { ClusterFilterModel } from '../cluster/filter/ClusterFilterModel';
import { ClusterFilterService } from '../cluster/filter/ClusterFilterService';
import { MultiselectModel } from '../cluster/filter/MultiselectModel';
import { VersionChecker } from '../config/VersionChecker';
import { SETTINGS } from '../config/settings';
import { FunctionFilterModel } from '../function/filter/FunctionFilterModel';
import { FunctionFilterService } from '../function/filter/FunctionFilterService';
import { LoadBalancerFilterModel } from '../loadBalancer/filter/LoadBalancerFilterModel';
import { LoadBalancerFilterService } from '../loadBalancer/filter/LoadBalancerFilterService';
import { ExecutionFilterModel } from '../pipeline/filter/ExecutionFilterModel';
import { SecurityGroupFilterModel } from '../securityGroup/filter/SecurityGroupFilterModel';
import { SecurityGroupFilterService } from '../securityGroup/filter/SecurityGroupFilterService';

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
