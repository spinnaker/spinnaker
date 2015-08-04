'use strict';

let angular = require('angular');

require('file!../../images/providers/logo_gce.png');

module.exports = angular.module('spinnaker.gce', [
  require('./serverGroups/details/gce/serverGroupDetails.gce.controller.js'),
  require('./serverGroups/configure/gce/ServerGroupCommandBuilder.js'),
  require('./serverGroups/configure/gce/wizard/CloneServerGroupCtrl.js'),
  require('./serverGroups/configure/gce/serverGroup.configure.gce.module.js'),
]).name;
