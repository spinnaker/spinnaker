export interface IOrchestratedItem {
  getValueFor: (k: string) => any;
  originalStatus: string;
  status: string;
  failureMessage: string;
  isCompleted: boolean;
  isRunning: boolean;
  isFailed: boolean;
  isActive: boolean;
  hasNotStarted: boolean;
  isCanceled: boolean;
  isSuspended: boolean;
  isPaused: boolean;
  runningTime: string;
  runningTimeInMs: number;
}
