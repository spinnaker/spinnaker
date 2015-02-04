'use strict';


angular.module('deckApp.notifications.service', [
  'deckApp.utils.rx'
])
  .factory('notificationsService', function(RxService) {
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
