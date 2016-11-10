'use strict';

let angular = require('angular');
import {NAMING_SERVICE} from 'core/naming/naming.service';

module.exports = angular.module('spinnaker.core.serverGroup.sequence.filter', [
  NAMING_SERVICE,
])
  .filter('serverGroupSequence', function(namingService) {
      return function(input) {
        if (!input) {
          return null;
        }
        return namingService.getSequence(input) || 'n/a';
      };
  });
