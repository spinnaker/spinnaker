'use strict';

import { FAST_PROPERTY_SEARCH_COMPONENT } from './view/fastPropertyFilterSearch.component';
import {FAST_PROPERTY_STATES} from './fastProperties.states';

let angular = require('angular');


require('./fastProperties.less');
require('./dataNav/fastPropertyFilterSearch.less');
require('../../netflix/canary/canary.less');

module.exports = angular
  .module('spinnaker.netflix.fastProperties', [
    FAST_PROPERTY_STATES,
    require('./dataNav/fastPropertyRollouts.controller.js'),
    require('./view/fastProperties.controller.js'),
    require('./view/fastPropertyPods.component.js'),
    require('./view/fastPropertyDetails.controller.js'),
    require('./fastProperty.dataSource'),
    require('./strategies/scopeLadder/scopeLadder.module.js'),
    require('./strategies/aca/aca.module.js'),
    FAST_PROPERTY_SEARCH_COMPONENT,
  ]);
