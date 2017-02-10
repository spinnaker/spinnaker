import {Instance} from 'core/domain/instance';

export interface IAppengineInstance extends Instance {
  name: string;
  id: string;
  account?: string;
  region?: string;
  instanceStatus: 'DYNAMIC' | 'RESIDENT' | 'UNKNOWN';
  launchTime: number;
  loadBalancers: string[];
  serverGroup: string;
  vmDebugEnabled: boolean;
  vmName: string;
  vmStatus: string;
  vmZoneName: string;
  qps: number;
  healthState: string;
  cloudProvider: string;
  errors: number;
  averageLatency: number;
}
