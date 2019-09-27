'use strict';

const angular = require('angular');
import { KUBERNETES_INSTANCE_CONTAINER_DETAIL } from './containerDetail.component';
module.exports = angular.module('spinnaker.instance.details.kubernetes', [
  require('./details.controller').name,
  KUBERNETES_INSTANCE_CONTAINER_DETAIL,
]);
