'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.serverGroup.details.networking', [
    require('./networking.directive.js'),
    require('./elasticIp.read.service.js'),
    require('./elasticIp.write.service.js')
  ]);
