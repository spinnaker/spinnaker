'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.notification.types.slack', [])
  .config(['notificationTypeConfigProvider', function(notificationTypeConfigProvider) {
    notificationTypeConfigProvider.registerNotificationType({
      label: 'Slack',
      key: 'slack',
      addressTemplateUrl: require('./additionalFields.html'),
    });
  }]);
