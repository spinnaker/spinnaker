'use strict';

import { FAST_PROPERTY_SEARCH_COMPONENT } from './view/fastPropertyFilterSearch.component';
import { FAST_PROPERTY_DETAILS_CONTROLLER } from './view/fastPropertyDetails.controller';
import {FAST_PROPERTY_STATES} from './fastProperties.states';
import { FAST_PROPERTY_PODS } from './view/fastPropertyPods.component';
import { FAST_PROPERTY_POD_TABLE } from './view/fastPropertyPodTable.component';
let angular = require('angular');

require('./fastProperties.less');
require('./dataNav/fastPropertyFilterSearch.less');
require('../../netflix/canary/canary.less');

module.exports = angular
  .module('spinnaker.netflix.fastProperties', [
    FAST_PROPERTY_STATES,
    require('./dataNav/fastPropertyRollouts.controller.js'),
    require('./view/fastProperties.controller.js'),
    FAST_PROPERTY_PODS,
    FAST_PROPERTY_POD_TABLE,
    FAST_PROPERTY_DETAILS_CONTROLLER,
    require('./fastProperty.dataSource'),
    FAST_PROPERTY_SEARCH_COMPONENT,
  ]);
