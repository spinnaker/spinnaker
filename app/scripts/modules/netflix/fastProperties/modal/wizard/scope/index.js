'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.fastpropterties.scope', [
    require('./scopeAppSelector.directive'),
    require('./scopeRegionSelector.directive'),
    require('./scopeStackSelector.directive'),
    require('./scopeClusterSelector.directive'),
    require('./scopeAsgSelector.directive'),
    require('./scopeAvailabilityZoneSelector.directive'),
    require('./scopeInstanceSelector.directive'),

  ]);
