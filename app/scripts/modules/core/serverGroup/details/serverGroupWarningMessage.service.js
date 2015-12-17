'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.serverGroup.details.warningMessage.service', [])
  .factory('serverGroupWarningMessageService', function($templateCache, $interpolate) {
    return {
      getMessage: (serverGroup) => {
        var template = $templateCache.get(require('./deleteLastServerGroupWarning.html'));
        return $interpolate(template)({deletingServerGroup: serverGroup});
      }
    };
  });
