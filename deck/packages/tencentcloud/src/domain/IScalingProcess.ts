export interface IScalingProcess {
  name: string;
  enabled?: boolean;
  description: string;
  suspensionDate?: number;
}

export interface ISuspendedProcess {
  processName: string;
  suspensionReason: string;
}
