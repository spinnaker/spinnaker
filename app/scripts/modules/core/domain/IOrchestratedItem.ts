export interface IOrchestratedItem {
  getValueFor: (k: string) => any;
  originalStatus: string;
  status: string;
  startTime: number;
  endTime: number;
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
  runningTimeInMs: number;
}
