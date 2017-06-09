export interface IGceAutoHealingPolicy {
  healthCheck?: string;
  initialDelaySec?: number;
  maxUnavailable?: IMaxUnavailable;
}

export interface IMaxUnavailable {
  fixed?: number;
  percent?: number;
}
