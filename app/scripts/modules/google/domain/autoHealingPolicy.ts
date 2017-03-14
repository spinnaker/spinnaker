export interface IGceAutoHealingPolicy {
  healthCheck?: string;
  initialDelaySec?: number;
  maxUnavailable?: IMaxUnavailable;
}

interface IMaxUnavailable {
  fixed?: number;
  percent?: number;
}
