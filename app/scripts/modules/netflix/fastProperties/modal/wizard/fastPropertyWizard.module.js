'use strict';

let angular = require('angular');

module.exports = angular
  .module('fastProperty.wizard.module', [
    require('./fastPropertyForm.directive'),
    require('./fastPropertyConstraintsSelector.directive'),
    require('./scope'),
  ]);
