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
}
