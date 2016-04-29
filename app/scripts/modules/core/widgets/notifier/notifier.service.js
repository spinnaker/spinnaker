'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.widgets.notifier.service', [
  ])
  .factory('notifierService', function (rx, $sce) {

    let messageStream = new rx.Subject();

    let publish = (message) => {
      let sanitized = $sce.trustAsHtml(message);
      messageStream.onNext(sanitized);
    };

    return {
      publish: publish,
      messageStream: messageStream,
    };
  });
