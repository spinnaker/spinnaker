'use strict';

angular
  .module('spinnaker.networking', [
    'spinnaker.networking.controller',
    'spinnaker.elasticIp.read.service',
    'spinnaker.elasticIp.write.service'
  ]);
