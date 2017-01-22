'use strict';

let angular = require('angular');

import {PROJECTS_STATES_CONFIG} from './projects.states';

module.exports = angular
  .module('spinnaker.projects', [
    PROJECTS_STATES_CONFIG,
  ]);
