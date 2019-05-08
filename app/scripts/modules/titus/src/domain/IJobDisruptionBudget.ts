export interface IJobTimeWindow {
  days: string[];
  hourlyTimeWindows: Array<{ startHour: number; endHour: number }>;
  timeZone: string;
}

export interface IJobDisruptionBudget {
  // policy options
  availabilityPercentageLimit?: { percentageOfHealthyContainers: number }; // default
  unhealthyTasksLimit?: { limitOfUnhealthyContainers: number };
  selfManaged?: { relocationTimeMs: number };
  relocationLimit?: { limit: number };

  rateUnlimited?: boolean;
  timeWindows: IJobTimeWindow[];
  containerHealthProviders: Array<{ name: string }>;
  ratePerInterval?: { intervalMs: number; limitPerInterval: number };
  ratePercentagePerInterval?: { intervalMs: number; percentageLimitPerInterval: number };
}
