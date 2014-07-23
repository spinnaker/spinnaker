'use strict';

angular.module('deckApp')
  .factory('tasks', function(RxService, $log, notifications) {
    var stream = new RxService.ReplaySubject();

    return {
      create: function(config) {
        var task = {
          // actual task details will come from pond/echo
          '$done': false,
          '$success': false,
          title: config.title,
          message: config.message,
          started: Date.now(),
          updated: Date.now(),
        };

        notifications.create({
          title: config.title,
          message: config.message,
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

      },

      observable: stream.all(),

    };

  });
