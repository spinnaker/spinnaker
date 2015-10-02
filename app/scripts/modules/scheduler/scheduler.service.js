'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.scheduler', [
  require('../utils/rx.js'),
  require('../core/cache/deckCacheFactory.js'),
  require('../config/settings.js')
])
  .factory('scheduler', function(RxService, settings, $q, $log, $window, $timeout) {
    var scheduler = new RxService.Subject();

    let lastRun = new Date().getTime();

    let source = RxService.Observable
      .timer(settings.pollSchedule)
      .repeat()
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
        runner();
      } else {
        $timeout(runner, settings.pollSchedule - (now - lastRun));
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

    document.addEventListener('visibilitychange', watchDocumentVisibility);
    $window.addEventListener('offline', suspendScheduler);
    $window.addEventListener('online', resumeScheduler);
    scheduler.onNext(true);

    return {
      get: function() { return scheduler; },
      subscribe: scheduler.subscribe.bind(scheduler),
      scheduleImmediate: scheduler.onNext.bind(scheduler),
      scheduleOnCompletion: function(promise) {
        var deferred = $q.defer();
        promise.then(
          function(result) {
            scheduler.onNext();
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
