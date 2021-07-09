import { IGceHealthCheck } from './healthCheck';

export interface IGceBackendService {
  name: string;
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
