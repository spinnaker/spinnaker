'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.fastproperties', [
    require('./fastProperties.controller.js'),
    require('./applicationProperties.controller.js'),
    require('./scopeSelect.directive.js'),
    require('./modal/deleteFastProperty.controller.js'),
    require('./fastPropertyRollouts.controller.js'),
    require('./fastProperties.data.controller.js'),
    require('./fastPropertyProgressBar.directive.js'),
    require('./modal/fastPropertyConstraint.directive.js'),
  ]).name;
