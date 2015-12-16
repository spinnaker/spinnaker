'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.notification.types.sms', [])
  .config(function(notificationTypeConfigProvider) {
    notificationTypeConfigProvider.registerNotificationType({
      label: 'SMS',
      key: 'sms',
      addressTemplateUrl: require('./additionalFields.html'),
    });
  });
