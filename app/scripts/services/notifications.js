'use strict';

angular.module('deckApp')
  .factory('notifications', function(RxService, $log) {
    var stream = new RxService.Subject();

    return {
      create: function(config) {
        stream.onNext({
          title: config.title,
          message: config.message,
          href: config.href,
          timestamp: Date.now(),
        });
      },
      subscribe: function(x) { stream.subscribe(x) },
    };
  });
