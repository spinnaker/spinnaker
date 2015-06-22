'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.config', [
    require('./modal/editApplication.controller.modal.js'),
    require('./modal/editNotification.controller.modal.js'),
    require('./config.controller.js'),
    require('./notification.service.js'),
    require('./notification.details.filter.js')
  ]);

