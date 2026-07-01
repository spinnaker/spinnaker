import type { IGceHealthCheck } from './healthCheck';

export interface IGceBackendService {
  name: string;
  account?: string;
  kind?: string;
  region?: string;
  selfLink?: string;
  backends: any[];
  healthCheck: IGceHealthCheck;
  sessionAffinity: string;
  portName: string;
  connectionDrainingTimeoutsSec: number;
}

export interface INamedPort {
  name: string;
  port: number;
}
