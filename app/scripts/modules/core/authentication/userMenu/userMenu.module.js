'use strict';

import {AUTHENTICATION_SERVICE} from '../authentication.service';

require('../../../../../fonts/spinnaker/icons.css');
require('./userMenu.less');

let angular = require('angular');

module.exports = angular.module('spinnaker.core.authentication.userMenu', [
  require('../../config/settings.js'),
  AUTHENTICATION_SERVICE,
  require('./userMenu.directive.js')
]);
