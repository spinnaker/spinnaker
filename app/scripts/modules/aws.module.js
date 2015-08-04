'use strict';

let angular = require('angular');

require('file!../../images/providers/logo_aws.png');

module.exports = angular.module('spinnaker.aws', [
  require('./serverGroups/details/aws/serverGroup.details.module.js'),
  require('./serverGroups/configure/aws/wizard/CloneServerGroup.aws.controller.js'),
  require('./serverGroups/configure/aws/serverGroup.configure.aws.module.js'),
]).name;
