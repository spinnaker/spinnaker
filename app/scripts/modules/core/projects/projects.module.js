'use strict';

const angular = require('angular');

import {PROJECTS_STATES_CONFIG} from './projects.states';
import './ProjectSearchResultFormatter';

module.exports = angular
  .module('spinnaker.projects', [
    PROJECTS_STATES_CONFIG,
  ]);
