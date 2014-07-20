'use strict';

angular.module('deckApp')
  .factory('notifications', function(RxService, $log) {
    var stream = new RxService.Subject();

    return {
      error: function(config) {
        config.$done = true;
        config.$success = false;
        stream.onNext(config);
      },
      alert: function(config) {
        config.$done = true;
        config.$success = true;
        stream.onNext(config);
      },
      observableTask: function(config) {
        config.$done = false;
        config.$success = false;
        config.observable.subscribe(function() {
          config.$done = true;
          config.$success = true;
        }, function(err) {
          config.$done = true;
          $log.debug(err);
          config.error = err;
        });
        stream.onNext(config);
      },
      promiseTask: function(config) {
        config.$done = false;
        config.$success = false;
        config.promise.then(function() {
          config.$success = true;
          config.$done = true;
        }, function(err) {
          config.$done = true;
          $log.debug(err);
          config.error = err;
        });
        stream.onNext(config);
      },
      subscribe: function(cb) { return stream.subscribe(cb); }
    };
  });
