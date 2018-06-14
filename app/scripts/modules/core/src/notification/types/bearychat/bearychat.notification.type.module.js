'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.notification.types.bearychat', [])
  .config(function(notificationTypeConfigProvider) {
    notificationTypeConfigProvider.registerNotificationType({
      label: 'Bearychat',
      key: 'bearychat',
      addressTemplateUrl: require('./additionalFields.html'),
    });
  });
