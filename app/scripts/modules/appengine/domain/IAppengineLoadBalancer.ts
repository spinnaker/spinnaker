import {ILoadBalancer} from 'core/domain/index';

export interface IAppengineLoadBalancer extends ILoadBalancer {
  credentials?: string;
  split: IAppengineTrafficSplit;
  migrateTraffic: boolean;
}

export interface IAppengineTrafficSplit {
  shardBy: 'UNSPECIFIED' | 'IP' | 'COOKIE';
  allocations: {[serverGroupName: string]: number};
}
