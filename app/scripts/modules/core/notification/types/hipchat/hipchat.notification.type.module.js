'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.notification.types.hipchat', [])
  .config(function(notificationTypeConfigProvider) {
    notificationTypeConfigProvider.registerNotificationType({
      label: 'HipChat',
      key: 'hipchat',
      addressTemplateUrl: require('./additionalFields.html')
    });
  });
