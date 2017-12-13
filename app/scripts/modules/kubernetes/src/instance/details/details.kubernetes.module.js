'use strict';

const angular = require('angular');
import { KUBERNETES_INSTANCE_CONTAINER_DETAIL } from './containerDetail.component';
module.exports = angular.module('spinnaker.instance.details.kubernetes', [
  require('./details.controller.js').name,
  KUBERNETES_INSTANCE_CONTAINER_DETAIL,
]);
