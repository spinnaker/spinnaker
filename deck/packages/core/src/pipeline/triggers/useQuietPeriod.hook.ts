import { REST } from '../../api';
import { useLatestPromise } from '../../presentation';

// Shape from back end
interface IQuietPeriodConfig {
  startTime: number;
  endTime: number;
  enabled: boolean;
  // Do not use in UI -- point-in-time data from back-end
  inQuietPeriod: boolean;
}

class QuietPeriodService {
  private static _quietPeriodConfig: PromiseLike<IQuietPeriodConfig>;
  static async quietPeriodConfig(): Promise<IQuietPeriodConfig> {
    this._quietPeriodConfig = this._quietPeriodConfig ?? REST('/capabilities/quietPeriod').get<IQuietPeriodConfig>();
    return await this._quietPeriodConfig;
  }
}

interface IQuietPeriod {
  currentStatus: 'UNKNOWN' | 'BEFORE_QUIET_PERIOD' | 'DURING_QUIET_PERIOD' | 'AFTER_QUIET_PERIOD' | 'NO_QUIET_PERIOD';
  startTime: number;
  endTime: number;
}

export function useQuietPeriod(): IQuietPeriod {
  const result = useLatestPromise(() => QuietPeriodService.quietPeriodConfig(), []);
  if (result.status !== 'RESOLVED') {
    return { currentStatus: 'UNKNOWN', startTime: undefined, endTime: undefined };
  }

  const { startTime, endTime, enabled } = result.result;
  if (!enabled || !startTime || startTime < 0 || !endTime || endTime < 0) {
    return { currentStatus: 'NO_QUIET_PERIOD', startTime: undefined, endTime: undefined };
  }

  const now = Date.now();
  const currentStatus =
    now < startTime ? 'BEFORE_QUIET_PERIOD' : now > endTime ? 'AFTER_QUIET_PERIOD' : 'DURING_QUIET_PERIOD';

  return { currentStatus, startTime, endTime };
}
