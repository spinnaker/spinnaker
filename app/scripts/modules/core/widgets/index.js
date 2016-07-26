'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.widgets', [
    require('./accountNamespaceClusterSelector.component'),
    require('./accountRegionClusterSelector.component'),
    require('./scopeClusterSelector.directive'),
    require('./notifier/notifier.component.js'),
    require('./spelText/spelText.decorator'),
    require('./spelText/numberInput.component')
  ]);
