import {ILoadBalancer} from 'core/domain/index';

export interface IAppengineLoadBalancer extends ILoadBalancer {
  credentials?: string;
  split?: IAppengineTrafficSplit;
  migrateTraffic: boolean;
}

export interface IAppengineTrafficSplit {
  shardBy: ShardBy
  allocations: {[serverGroupName: string]: number};
}

export type ShardBy = 'UNSPECIFIED' | 'IP' | 'COOKIE';
