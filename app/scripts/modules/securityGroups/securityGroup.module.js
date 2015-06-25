'use strict';

angular
  .module('spinnaker.securityGroup', [
    'spinnaker.securityGroup.all.controller',
    'spinnaker.securityGroup.single.controller',
    'spinnaker.securityGroup.rollup',
    'spinnaker.securityGroup.read.service',
    'spinnaker.securityGroup.write.service',
    'spinnaker.securityGroup.counts',
    'spinnaker.securityGroup.aws.details.controller',
    'spinnaker.securityGroup.aws.edit.controller',
    'spinnaker.securityGroup.aws.create.controller',
    'spinnaker.securityGroup.navigation.controller',
    'spinnaker.securityGroup.clone.controller',
    'spinnaker.securityGroup.baseConfig.controller'
  ]);
