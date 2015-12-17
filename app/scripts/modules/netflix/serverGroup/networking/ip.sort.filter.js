'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.aws.serverGroup.details.networking.ip.sort.filter', [])
  .filter('ipSorter', function() {

    return function(input) {
      if (input && input.length) {
        var result = input.slice();
        result.sort(function (a, b) {
          if (a.address && b.address) {
            var aParts = a.address.split('.'),
              bParts = b.address.split('.');

            for (var i = 0; i < aParts.length; i++) {
              if (aParts[i] !== bParts[i]) {
                return parseInt(aParts[i]) - parseInt(bParts[i]);
              }
            }
          }
          return 0;
        });
        return result;
      }
    };
  });
