import { IGceHealthCheckKind } from './healthCheck';

export interface IGceAutoHealingPolicy {
  healthCheck?: string; // received from server as health check URL, but posted as health check name
  healthCheckKind?: IGceHealthCheckKind; // used by Clouddriver to disambiguate health checks
  healthCheckUrl?: string; // used by Deck as unique ID
  initialDelaySec?: number;
  maxUnavailable?: IMaxUnavailable;
}

export interface IMaxUnavailable {
  fixed?: number;
  percent?: number;
}
