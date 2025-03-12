export interface IGceHealthCheck {
  account: string;
  name: string;
  requestPath: string;
  port: number;
  healthCheckType: string;
  checkIntervalSec: number;
  timeoutSec: number;
  unhealthyThreshold: number;
  healthyThreshold: number;
  kind: IGceHealthCheckKind;
  selfLink: string;
}

export enum IGceHealthCheckKind {
  healthCheck = 'healthCheck',
  httpHealthCheck = 'httpHealthCheck',
  httpsHealthCheck = 'httpsHealthCheck',
}
