import { IInstance } from '@spinnaker/core';

interface ITitusInstancePlacement {
  containerIp: string;
  host: string;
  region: string;
  zone: string;
}

interface InsightAction {
  label: string;
  url: string;
}

export interface ITitusInstance extends IInstance {
  containerIp: string;
  instanceType?: string;
  insightActions?: InsightAction[];
  ipv6Address?: string;
  jobId?: string;
  jobName?: string;
  placement: ITitusInstancePlacement;
  privateIpAddress?: string;
}
