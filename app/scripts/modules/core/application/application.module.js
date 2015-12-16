'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.application', [
    require('./application.controller.js'),
    require('./applications.controller.js'),
    require('./config/applicationConfig.controller.js'),
    require('./modal/createApplication.modal.controller.js'),
  ]);
