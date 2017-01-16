export interface ITimedItem {
  startTime: number;
  endTime: number;
  runningTimeInMs: number;
}

export interface IOrchestratedItemVariable {
  key: string;
  value: any;
}

export interface IOrchestratedItem extends ITimedItem {
  getValueFor: (k: string) => any;
  originalStatus: string;
  status: string;
  failureMessage: string;
  isCompleted: boolean;
  isRunning: boolean;
  isFailed: boolean;
  isStopped: boolean;
  isActive: boolean;
  hasNotStarted: boolean;
  isCanceled: boolean;
  isSuspended: boolean;
  isPaused: boolean;
  runningTime: string;
}
