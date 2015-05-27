'use strict';

angular.module('spinnaker.serverGroup.details.aws', [
  'spinnaker.notifications',
  'spinnaker.serverGroup.details.aws.controller',
  'spinnaker.serverGroup.details.aws.autoscaling.process',
  'spinnaker.serverGroup.details.aws.autoscaling.process.controller',
]);
