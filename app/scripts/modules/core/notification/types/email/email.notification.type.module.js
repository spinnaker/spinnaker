'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.notification.types.email', [])
  .config(function(notificationTypeConfigProvider) {
    notificationTypeConfigProvider.registerNotificationType({
      label: 'Email',
      key: 'email',
      addressTemplateUrl: require('./additionalFields.html'),
    });
  });
