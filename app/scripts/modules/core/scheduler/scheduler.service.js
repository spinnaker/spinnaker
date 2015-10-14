'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.scheduler', [
  require('../utils/rx.js'),
  require('../config/settings.js')
])
  .factory('scheduler', function(RxService, settings, $q, $log, $window, $timeout) {
    var scheduler = new RxService.Subject();

    let lastRun = new Date().getTime();

    let source = RxService.Observable
      .timer(0, settings.pollSchedule)
      .pausable(scheduler);

    let runner = () => {
      lastRun = new Date().getTime();
      $log.debug('refreshing');
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
      get: function() { return scheduler; },
      subscribe: scheduler.subscribe.bind(scheduler),
      scheduleImmediate: scheduleImmediate,
      scheduleOnCompletion: function(promise) {
        var deferred = $q.defer();
        promise.then(
          function(result) {
            scheduleImmediate();
            deferred.resolve(result);
          },
          function(error) {
            deferred.reject(error);
          }
        );
        return deferred.promise;
      },
    };
  })
  .name;
