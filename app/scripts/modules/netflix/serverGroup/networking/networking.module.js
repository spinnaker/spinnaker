'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.aws.serverGroup.details.networking', [
    require('./networking.controller.js'),
    require('./elasticIp.read.service.js'),
    require('./elasticIp.write.service.js')
  ]);
