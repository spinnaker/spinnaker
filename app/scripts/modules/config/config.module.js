'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.config', [
    require('./modal/editApplication.controller.modal.js'),
    require('./config.controller.js')
  ])
  .name;

