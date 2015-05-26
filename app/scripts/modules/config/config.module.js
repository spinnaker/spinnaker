'use strict';

angular
  .module('spinnaker.config', [
    'spinnaker.editApplication.modal.controller',
    'spinnaker.editNotification.modal.controller',
    'spinnaker.config.controller',
    'spinnaker.config.notification.service',
    'spinnaker.config.notification.details.filter'
  ]);

