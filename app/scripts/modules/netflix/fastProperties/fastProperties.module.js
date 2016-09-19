'use strict';

let angular = require('angular');
require('./fastProperties.less');
require('../../netflix/canary/canary.less');

module.exports = angular
  .module('spinnaker.netflix.fastProperties', [
    require('./states.js'),
    require('./fastPropertyDetails.controller.js'),
    require('./dataNav/fastProperties.controller.js'),
    require('./modal/fastPropertyUpsert.controller.js'),
    require('./applicationProperties.controller.js'),
    require('./scopeSelect.directive.js'),
    require('./modal/deleteFastProperty.controller.js'),
    require('./dataNav/fastPropertyRollouts.controller.js'),
    require('./dataNav/fastProperties.data.controller.js'),
    require('./fastPropertyProgressBar.directive.js'),
    require('./modal/fastPropertyConstraint.directive.js'),
    require('./modal/fastPropertyStrategySelector.directive.js'),
    require('./strategies/scopeLadder/scopeLadder.module.js'),
    require('./strategies/aca/aca.module.js'),
    require('./fastPropertyPromotion.directive.js'),
    require('./modal/wizard/fastPropertyWizard.module'),
    require('./regionSelector.component'),
    require('./stackSelector.component'),
    require('./clusterSelector.component'),
    require('./asgSelector.component'),
    require('./fastPropertyPod.component'),
    require('./fastProperty.dataSource'),
  ]);
