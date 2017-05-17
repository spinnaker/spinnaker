'use strict';

import {AUTHENTICATION_SERVICE} from '../authentication.service';

import './userMenu.less';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.authentication.userMenu', [
  AUTHENTICATION_SERVICE,
  require('./userMenu.directive.js')
]);
