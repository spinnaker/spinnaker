'use strict';

angular
  .module('deckApp.config', [
    'deckApp.editApplication.modal.controller',
    'deckApp.editNotification.modal.controller',
    'deckApp.config.controller',
    'deckApp.config.notification.service',
    'deckApp.config.notification.details.filter'
  ]);

