'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.notification.types.hipchat', [])
  .config(['notificationTypeConfigProvider', function(notificationTypeConfigProvider) {
    notificationTypeConfigProvider.registerNotificationType({
      label: 'HipChat',
      key: 'hipchat',
      addressTemplateUrl: require('./additionalFields.html'),
    });
  }]);
