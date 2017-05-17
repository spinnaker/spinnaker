'use strict';

import {Subject} from 'rxjs';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.widgets.notifier.service', [
  ])
  .factory('notifierService', function ($sce) {

    let messageStream = new Subject();

    let publish = (message) => {
      message.body = $sce.trustAsHtml(message.body);
      messageStream.next(message);
    };

    let clear = (key) => {
      messageStream.next({action: 'remove', key: key});
    };

    return {
      publish: publish,
      clear: clear,
      messageStream: messageStream,
    };
  });
