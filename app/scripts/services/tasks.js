'use strict';

angular.module('deckApp')
  .factory('tasks', function(RxService, $log, notifications) {
    var stream = new RxService.Subject();

    return {
      create: function(config) {
        var task = {
          '$done': false,
          '$success': false,
          title: config.title,
          message: config.message
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

      subscribe: stream.subscribe

    };

  });
