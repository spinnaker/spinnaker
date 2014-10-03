'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .factory('notifications', function(RxService) {
    var stream = new RxService.Subject();

    return {
      create: function(config) {
        stream.onNext({
          title: config.title,
          message: config.message,
          href: config.href,
          timestamp: config.hideTimestamp ? '' : Date.now(),
          autoDismiss: config.autoDismiss,
          strong: config.strong
        });
      },
      subscribe: stream.subscribe.bind(stream),
    };
  });
