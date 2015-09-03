'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.config', [
    require('./modal/editApplication.controller.modal.js'),
    require('./applicationConfig.controller.js')
  ])
  .name;

