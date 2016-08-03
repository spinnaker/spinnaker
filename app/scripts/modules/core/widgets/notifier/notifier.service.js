'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.widgets.notifier.service', [
  ])
  .factory('notifierService', function (rx, $sce) {

    let messageStream = new rx.Subject();

    let publish = (message) => {
      message.body = $sce.trustAsHtml(message.body);
      messageStream.onNext(message);
    };

    let clear = (key) => {
      messageStream.onNext({action: 'remove', key: key});
    };

    return {
      publish: publish,
      clear: clear,
      messageStream: messageStream,
    };
  });
