'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.networking', [
    require('./networking.controller.js'),
    require('./elasticIp.read.service.js'),
    require('./elasticIp.write.service.js')
  ]).name;
