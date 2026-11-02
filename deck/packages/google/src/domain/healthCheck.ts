export interface IGceHealthCheck {
  account: string;
  name: string;
  requestPath: string;
  grpcServiceName: string;
  port: number;
  portSpecification: string;
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
