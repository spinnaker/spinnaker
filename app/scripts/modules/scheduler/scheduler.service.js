'use strict';

angular.module('spinnaker.scheduler', ['spinnaker.utils.rx', 'spinnaker.settings'])
  .factory('scheduler', function(RxService, settings, $q) {
    var scheduler = new RxService.Subject();

    RxService.Observable
      .timer(settings.pollSchedule)
      .repeat()
      .subscribe(function() {
        scheduler.onNext();
      });

    return {
      get: function() { return scheduler; },
      subscribe: scheduler.subscribe.bind(scheduler),
      scheduleImmediate: scheduler.onNext.bind(scheduler),
      subscribeEveryN: function(n, fn) {
        return scheduler
          .bufferWithCount(n)
          .subscribe(fn);
      },
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
  });
