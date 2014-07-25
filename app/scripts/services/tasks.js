'use strict';

angular.module('deckApp')
  .factory('tasks', function(RxService, $log, notifications) {

    var stream = new RxService.ReplaySubject();

    return {
      create: function(task, observable, config) {

        notifications.create({
          description: config.description,
          steps: config.steps,
          href: config.href
        });

        config.observable.subscribe(function(data) {
          task.$done = true;
          task.$success = true;
          notifications.create(config.success(data));
        }, function(err) {
          $log.error(err);
          task.$done = true;
          task.$success = false;
          notifications.create(config.failure(err));
        });

        stream.onNext(task);

      },

      each: stream,

      all: stream.scan([], function(acc, x) {
        return acc.concat([x]);
      }),

    };

  });
