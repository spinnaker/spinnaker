'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.scheduler', [
  require('../utils/rx.js'),
  require('../config/settings.js')
])
  .factory('schedulerFactory', function(rx, settings, $log, $window, $timeout) {

    function createScheduler() {
      var scheduler = new rx.Subject();

      let lastRun = new Date().getTime();

      // When creating the timer, use last run as the dueTime (first arg); zero can lead to concurrency issues
      // where the scheduler will fire shortly after being subscribed to, resulting in surprising immediate refreshes
      let source = rx.Observable
        .timer(settings.pollSchedule, settings.pollSchedule)
        .pausable(scheduler);

      let runner = () => {
        lastRun = new Date().getTime();
        scheduler.onNext(true);
      };

      source.subscribe(runner);

      let suspendScheduler = () => {
        $log.debug('auto refresh suspended');
        source.pause();
      };

      let resumeScheduler = () => {
        let now = new Date().getTime();
        $log.debug('auto refresh resumed');
        if (now - lastRun > settings.pollSchedule) {
          source.resume();
        } else {
          $timeout(() => source.resume(), settings.pollSchedule - (now - lastRun));
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
        runner();
        source.pause();
        $timeout(() => source.resume(), settings.pollSchedule);
      };

      document.addEventListener('visibilitychange', watchDocumentVisibility);
      $window.addEventListener('offline', suspendScheduler);
      $window.addEventListener('online', resumeScheduler);
      scheduler.onNext(true);

      return {
        subscribe: scheduler.subscribe.bind(scheduler),
        scheduleImmediate: scheduleImmediate,
        dispose: () => {
          scheduler.onNext(false);
          scheduler.dispose();
          scheduler = null;
          source = null;
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
