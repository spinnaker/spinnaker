'use strict';

var angular = require('angular');

angular.module('deckApp')
  .factory('scheduler', function(RxService, settings, $q) {
    var scheduler = new RxService.Subject();

    RxService.Observable
      .timer(settings.pollSchedule)
      .repeat()
      .subscribe(function() {
        scheduler.onNext();
      });

    return {
      subscribe: scheduler.subscribe.bind(scheduler),
      scheduleImmediate: scheduler.onNext.bind(scheduler),
      scheduleOnCompletion: function(promise) {
        var deferred = $q.defer();
        promise.then(function(result) {
          scheduler.onNext();
          deferred.resolve(result);
        });
        return deferred.promise;
      },
    };
  });
