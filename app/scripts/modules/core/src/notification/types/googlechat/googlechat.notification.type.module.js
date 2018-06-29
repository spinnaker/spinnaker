'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.notification.types.googlechat', [])
  .config(function(notificationTypeConfigProvider) {
    notificationTypeConfigProvider.registerNotificationType({
      label: 'GoogleChat',
      key: 'googlechat',
      addressTemplateUrl: require('./additionalFields.html'),
    });
  });
