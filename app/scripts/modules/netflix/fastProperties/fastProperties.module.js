'use strict';

let angular = require('angular');
require('./fastProperties.less');
require('../../netflix/canary/canary.less');

module.exports = angular
  .module('spinnaker.netflix.fastProperties', [
    require('./states.js'),
    require('./fastProperties.controller.js'),
    require('./modal/fastPropertyUpsert.controller.js'),
    require('./applicationProperties.controller.js'),
    require('./scopeSelect.directive.js'),
    require('./modal/deleteFastProperty.controller.js'),
    require('./fastPropertyRollouts.controller.js'),
    require('./fastProperties.data.controller.js'),
    require('./fastPropertyProgressBar.directive.js'),
    require('./modal/fastPropertyConstraint.directive.js'),
    require('./modal/fastPropertyStrategySelector.directive.js'),
    require('./strategies/scopeLadder/scopeLadder.module.js'),
    require('./strategies/aca/aca.module.js'),
    require('./fastPropertyPromotion.directive.js'),
    require('./modal/wizard/fastPropertyWizard.module')
  ]);
