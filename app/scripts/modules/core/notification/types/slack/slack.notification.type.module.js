'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.notification.types.slack', [])
  .config(function(notificationTypeConfigProvider) {
    notificationTypeConfigProvider.registerNotificationType({
      label: 'Slack',
      key: 'slack',
      addressTemplateUrl: require('./additionalFields.html')
    });
  });
