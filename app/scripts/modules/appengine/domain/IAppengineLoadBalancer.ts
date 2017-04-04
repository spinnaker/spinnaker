import {ILoadBalancer} from 'core/domain/index';

export interface IAppengineLoadBalancer extends ILoadBalancer {
  credentials?: string;
  split?: IAppengineTrafficSplit;
  migrateTraffic: boolean;
  dispatchRules?: IAppengineDispatchRule[];
}

export interface IAppengineTrafficSplit {
  shardBy: ShardBy;
  allocations: {[serverGroupName: string]: number};
}

export interface IAppengineDispatchRule {
  domain: string;
  path: string;
  service: string;
}

export type ShardBy = 'UNSPECIFIED' | 'IP' | 'COOKIE';
