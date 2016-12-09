import {LoadBalancer} from 'core/domain/index';

export interface IAppengineLoadBalancer extends LoadBalancer {
  split: IAppengineTrafficSplit;
  migrateTraffic: boolean;
}

export interface IAppengineTrafficSplit {
  shardBy: 'UNSPECIFIED' | 'IP' | 'COOKIE';
  allocations: {[serverGroupName: string]: number};
}