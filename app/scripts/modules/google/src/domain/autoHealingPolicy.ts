import { IGceHealthCheckKind } from 'google/domain/healthCheck';

export interface IGceAutoHealingPolicy {
  healthCheck?: string;
  healthCheckKind?: IGceHealthCheckKind;
  initialDelaySec?: number;
  maxUnavailable?: IMaxUnavailable;
}

export interface IMaxUnavailable {
  fixed?: number;
  percent?: number;
}
