'use strict';

import { FAST_PROPERTY_SEARCH_COMPONENT } from './dataNav/fastPropertyFilterSearch.component';
import {FAST_PROPERTY_STATES} from './fastProperties.states';

let angular = require('angular');


require('./fastProperties.less');
require('./dataNav/fastPropertyFilterSearch.less');
require('../../netflix/canary/canary.less');

module.exports = angular
  .module('spinnaker.netflix.fastProperties', [
    FAST_PROPERTY_STATES,
    require('./fastPropertyDetails.controller.js'),
    require('./dataNav/fastProperties.controller.js'),
    require('./modal/fastPropertyUpsert.controller.js'),
    require('./applicationProperties.controller.js'),
    require('./scopeSelect.directive.js'),
    require('./modal/deleteFastProperty.controller.js'),
    require('./dataNav/fastPropertyRollouts.controller.js'),
    require('./fastPropertyProgressBar.directive.js'),
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
    require('./globalFastPropertyPods.component'),
    require('./globalFastPropertyDetails.controller'),
    FAST_PROPERTY_SEARCH_COMPONENT,
  ]);
