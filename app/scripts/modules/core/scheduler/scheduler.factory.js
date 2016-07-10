'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.scheduler', [
  require('../utils/rx.js'),
  require('../config/settings.js')
])
  .factory('schedulerFactory', function(rx, settings, $log, $window, $timeout) {

    function createScheduler(pollSchedule = settings.pollSchedule) {
      var scheduler = new rx.Subject();

      let lastRunTimestamp = new Date().getTime();
      let pendingRun = null;
      let suspended = false;

      // When creating the timer, use last run as the dueTime (first arg); zero can lead to concurrency issues
      // where the scheduler will fire shortly after being subscribed to, resulting in surprising immediate refreshes
      let source = rx.Observable
        .timer(pollSchedule, pollSchedule);

      let run = () => {
        if (suspended) {
          return;
        }
        $timeout.cancel(pendingRun);
        lastRunTimestamp = new Date().getTime();
        scheduler.onNext(true);
        pendingRun = null;
      };

      source.subscribe(run);

      let suspendScheduler = () => {
        $log.debug('auto refresh suspended');
        suspended = true;
      };

      let scheduleNextRun = (delay) => {
        // do not schedule another run if a run is pending
        suspended = false;
        pendingRun = pendingRun || $timeout(run, delay);
      };

      let resumeScheduler = () => {
        suspended = false;
        let now = new Date().getTime();
        $log.debug('auto refresh resumed');
        if (now - lastRunTimestamp > pollSchedule) {
          run();
        } else {
          scheduleNextRun(pollSchedule - (now - lastRunTimestamp));
        }
      };

      let watchDocumentVisibility = () => {
        $log.debug('document visibilityState changed to: ', document.visibilityState);
        if (document.visibilityState === 'visible') {
          resumeScheduler();
        } else {
          suspendScheduler();
        }
      };

      let scheduleImmediate = () => {
        run();
        suspended = true;
        scheduleNextRun(pollSchedule);
      };

      document.addEventListener('visibilitychange', watchDocumentVisibility);
      $window.addEventListener('offline', suspendScheduler);
      $window.addEventListener('online', resumeScheduler);
      scheduler.onNext(true);

      return {
        subscribe: scheduler.subscribe.bind(scheduler),
        scheduleImmediate: scheduleImmediate,
        dispose: () => {
          suspended = true;
          if (scheduler) {
            scheduler.onNext(false);
            scheduler.dispose();
          }
          scheduler = null;
          source = null;
          $timeout.cancel(pendingRun);
          document.removeEventListener('visibilitychange', watchDocumentVisibility);
          $window.removeEventListener('offline', suspendScheduler);
          $window.removeEventListener('online', resumeScheduler);
        }
      };
    }

    return {
      createScheduler: createScheduler
    };
  });
