import type { ILoadBalancer } from '@spinnaker/core';

export interface ICloudrunLoadBalancer extends ILoadBalancer {
  credentials?: string;
  split?: ICloudrunTrafficSplit;
  migrateTraffic: boolean;
  dispatchRules?: ICloudrunDispatchRule[];
}

export interface ICloudrunTrafficSplit {
  trafficTargets: [{ revisionName: string; percent: number }];
}

export interface ICloudrunDispatchRule {
  domain: string;
  path: string;
  service: string;
}
