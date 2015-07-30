'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.application', [
    require('./application.controller.js'),
    require('./applications.controller.js'),
    require('./modal/createApplication.modal.controller.js'),
  ]).name;
