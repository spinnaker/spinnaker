'use strict';

import { FAST_PROPERTY_SEARCH_COMPONENT } from './view/filter/fastPropertyFilterSearch.component';
import { FAST_PROPERTY_DETAILS_CONTROLLER } from './view/details/fastPropertyDetails.controller';
import { CREATE_FAST_PROPERTY_WIZARD_CONTROLLER } from './wizard/createFastPropertyWizard.controller';
import { FAST_PROPERTY_STATES} from './fastProperties.states';
let angular = require('angular');

import './fastProperties.less';
import './global/fastPropertyFilterSearch.less';
import 'netflix/canary/canary.less';

module.exports = angular
  .module('spinnaker.netflix.fastProperties', [
    FAST_PROPERTY_STATES,
    CREATE_FAST_PROPERTY_WIZARD_CONTROLLER,
    FAST_PROPERTY_DETAILS_CONTROLLER,
    require('./fastProperty.dataSource'),
    FAST_PROPERTY_SEARCH_COMPONENT,
  ]);
