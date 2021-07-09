export interface ITimedItem {
  startTime: number;
  endTime: number;
  /**
   * runningTimeInMs will be null if the item has not started
   */
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
  isBuffered: boolean;
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
