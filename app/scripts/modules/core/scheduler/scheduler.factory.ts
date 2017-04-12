import {ILogService, IPromise, ITimeoutService, IWindowService, module} from 'angular';
import {Observable, Subject} from 'rxjs';

import {SETTINGS} from 'core/config/settings';

export class SchedulerFactory {
  static get $inject(): string[] { return ['$log', '$window', '$timeout']; }
  constructor(private $log: ILogService,
              private $window: IWindowService,
              private $timeout: ITimeoutService) {}

  public createScheduler(pollSchedule = SETTINGS.pollSchedule) {
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
      this.$timeout.cancel(pendingRun);
      lastRunTimestamp = new Date().getTime();
      scheduler.next(true);
      pendingRun = null;
    };

    source.subscribe(run);

    const suspendScheduler = (): void => {
      this.$log.debug('auto refresh suspended');
      suspended = true;
    };

    const scheduleNextRun = (delay: number) => {
      // do not schedule another run if a run is pending
      suspended = false;
      pendingRun = pendingRun || this.$timeout(run, delay);
    };

    const resumeScheduler = (): void => {
      suspended = false;
      const now = new Date().getTime();
      this.$log.debug('auto refresh resumed');
      if (now - lastRunTimestamp > pollSchedule) {
        run();
      } else {
        scheduleNextRun(pollSchedule - (now - lastRunTimestamp));
      }
    };

    const watchDocumentVisibility = (): void => {
      this.$log.debug('document visibilityState changed to: ', document.visibilityState);
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
    this.$window.addEventListener('offline', suspendScheduler);
    this.$window.addEventListener('online', resumeScheduler);
    scheduler.next(true);

    return {
      subscribe: scheduler.subscribe.bind(scheduler),
      scheduleImmediate: scheduleImmediate,
      unsubscribe: () => {
        suspended = true;
        if (scheduler) {
          scheduler.next(false);
          scheduler.unsubscribe();
        }
        scheduler = null;
        source = null;
        this.$timeout.cancel(pendingRun);
        document.removeEventListener('visibilitychange', watchDocumentVisibility);
        this.$window.removeEventListener('offline', suspendScheduler);
        this.$window.removeEventListener('online', resumeScheduler);
      }
    };
  }
}

export const SCHEDULER_FACTORY = 'spinnaker.core.scheduler';
module(SCHEDULER_FACTORY, [

]).factory('schedulerFactory', ($log: ILogService, $window: IWindowService, $timeout: ITimeoutService) =>
                                new SchedulerFactory($log, $window, $timeout));

export let schedulerFactory: SchedulerFactory = undefined;
export const SchedulerFactoryInject = ($injector: any) => {
    schedulerFactory = <SchedulerFactory>$injector.get('schedulerFactory');
};
