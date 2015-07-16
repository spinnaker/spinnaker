'use strict';

angular.module('spinnaker.serverGroup.configure.gce', [
  'spinnaker.account',
  'spinnaker.serverGroup.configure.gce.deployInitialization.controller',
  'spinnaker.serverGroups.basicSettings.controller',
  'spinnaker.gce.serverGroup.configure.service',
]);
