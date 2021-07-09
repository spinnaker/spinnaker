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
  instancePort?: string;
  instanceType?: string;
  insightActions?: InsightAction[];
  ipv6Address?: string;
  jobId?: string;
  jobName?: string;
  permanentIps?: string[];
  placement: ITitusInstancePlacement;
  privateDnsName?: string;
  privateIpAddress?: string;
  publicDnsName?: string;
  publicIpAddress?: string;
  titusUiEndpoint: string;
}
