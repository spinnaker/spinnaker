import type { Subscription } from 'rxjs';
import { Subject, timer as observableTimer } from 'rxjs';

import { SETTINGS } from '../config/settings';
import { diagnosticLogger } from '../utils/diagnosticLogger';

export interface IScheduler {
  subscribe: (next?: () => void, error?: (error: any) => void, complete?: () => void) => Subscription;
  scheduleImmediate: () => void;
  unsubscribe: () => void;
}

export class SchedulerFactory {
  public static createScheduler(pollSchedule = SETTINGS.pollSchedule || 30000): IScheduler {
    const activeWindow = window;
    let scheduler = new Subject();

    let lastRunTimestamp = new Date().getTime();
    let pendingRun: number = null;
    let disposed = false;
    let suspended = false;

    // When creating the timer, use last run as the dueTime (first arg); zero can lead to concurrency issues
    // where the scheduler will fire shortly after being subscribed to, resulting in surprising immediate refreshes
    const source = observableTimer(pollSchedule, pollSchedule);

    const cancelPendingRun = (): void => {
      if (pendingRun !== null) {
        activeWindow.clearTimeout(pendingRun);
        pendingRun = null;
      }
    };

    const run = (): void => {
      if (disposed || suspended) {
        return;
      }
      cancelPendingRun();
      lastRunTimestamp = new Date().getTime();
      if (disposed) {
        return;
      }
      scheduler.next(true);
      if (disposed) {
        return;
      }
    };

    const sourceSubscription = source.subscribe(run);

    const suspendScheduler = (): void => {
      if (disposed) {
        return;
      }
      diagnosticLogger.debug('auto refresh suspended');
      suspended = true;
    };

    const scheduleNextRun = (delay: number) => {
      if (disposed) {
        return;
      }
      // do not schedule another run if a run is pending
      suspended = false;
      if (pendingRun === null) {
        const nextRun = activeWindow.setTimeout(() => {
          if (pendingRun !== nextRun) {
            return;
          }
          pendingRun = null;
          run();
        }, delay);
        if (disposed) {
          activeWindow.clearTimeout(nextRun);
          return;
        }
        pendingRun = nextRun;
      }
    };

    const resumeScheduler = (): void => {
      if (disposed) {
        return;
      }
      suspended = false;
      const now = new Date().getTime();
      diagnosticLogger.debug('auto refresh resumed');
      if (now - lastRunTimestamp > pollSchedule) {
        run();
      } else {
        scheduleNextRun(pollSchedule - (now - lastRunTimestamp));
      }
    };

    const watchDocumentVisibility = (): void => {
      if (disposed) {
        return;
      }
      diagnosticLogger.debug('document visibilityState changed to: ', document.visibilityState);
      if (document.visibilityState === 'visible') {
        resumeScheduler();
      } else {
        suspendScheduler();
      }
    };

    const scheduleImmediate = (): void => {
      if (disposed) {
        return;
      }
      run();
      if (disposed) {
        return;
      }
      suspended = true;
      scheduleNextRun(pollSchedule);
    };

    document.addEventListener('visibilitychange', watchDocumentVisibility);
    activeWindow.addEventListener('offline', suspendScheduler);
    activeWindow.addEventListener('online', resumeScheduler);
    scheduler.next(true);

    return {
      subscribe: (next, error, complete) =>
        scheduler.subscribe(
          () => {
            if (!disposed) {
              next?.();
            }
          },
          error,
          complete,
        ),
      scheduleImmediate,
      unsubscribe: () => {
        if (disposed) {
          return;
        }
        disposed = true;
        suspended = true;
        sourceSubscription.unsubscribe();
        if (scheduler) {
          scheduler.unsubscribe();
        }
        scheduler = null;
        cancelPendingRun();
        document.removeEventListener('visibilitychange', watchDocumentVisibility);
        activeWindow.removeEventListener('offline', suspendScheduler);
        activeWindow.removeEventListener('online', resumeScheduler);
      },
    };
  }
}
