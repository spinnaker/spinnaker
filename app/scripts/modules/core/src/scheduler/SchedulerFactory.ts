import { IPromise } from 'angular';
import { Observable, Subject, Subscription } from 'rxjs';
import { $log, $window, $timeout } from 'ngimport';

import { SETTINGS } from 'core/config/settings';

export interface IScheduler {
  subscribe: (next?: () => void, error?: (error: any) => void, complete?: () => void) => Subscription;
  scheduleImmediate: () => void;
  unsubscribe: () => void;
}

export class SchedulerFactory {
  public static createScheduler(pollSchedule = SETTINGS.pollSchedule): IScheduler {
    let scheduler = new Subject();

    let lastRunTimestamp = new Date().getTime();
    let pendingRun: IPromise<void> = null;
    let suspended = false;

    // When creating the timer, use last run as the dueTime (first arg); zero can lead to concurrency issues
    // where the scheduler will fire shortly after being subscribed to, resulting in surprising immediate refreshes
    let source = Observable.timer(pollSchedule, pollSchedule);

    const run = (): void => {
      if (suspended) {
        return;
      }
      $timeout.cancel(pendingRun);
      lastRunTimestamp = new Date().getTime();
      scheduler.next(true);
      pendingRun = null;
    };

    source.subscribe(run);

    const suspendScheduler = (): void => {
      $log.debug('auto refresh suspended');
      suspended = true;
    };

    const scheduleNextRun = (delay: number) => {
      // do not schedule another run if a run is pending
      suspended = false;
      pendingRun = pendingRun || $timeout(run, delay);
    };

    const resumeScheduler = (): void => {
      suspended = false;
      const now = new Date().getTime();
      $log.debug('auto refresh resumed');
      if (now - lastRunTimestamp > pollSchedule) {
        run();
      } else {
        scheduleNextRun(pollSchedule - (now - lastRunTimestamp));
      }
    };

    const watchDocumentVisibility = (): void => {
      $log.debug('document visibilityState changed to: ', document.visibilityState);
      if (document.visibilityState === 'visible') {
        resumeScheduler();
      } else {
        suspendScheduler();
      }
    };

    const scheduleImmediate = (): void => {
      run();
      suspended = true;
      scheduleNextRun(pollSchedule);
    };

    document.addEventListener('visibilitychange', watchDocumentVisibility);
    $window.addEventListener('offline', suspendScheduler);
    $window.addEventListener('online', resumeScheduler);
    scheduler.next(true);

    return {
      subscribe: scheduler.subscribe.bind(scheduler),
      scheduleImmediate,
      unsubscribe: () => {
        suspended = true;
        if (scheduler) {
          scheduler.unsubscribe();
        }
        scheduler = null;
        source = null;
        $timeout.cancel(pendingRun);
        document.removeEventListener('visibilitychange', watchDocumentVisibility);
        $window.removeEventListener('offline', suspendScheduler);
        $window.removeEventListener('online', resumeScheduler);
      },
    };
  }
}
