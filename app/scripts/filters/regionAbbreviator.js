'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .filter('regionAbbreviator', function () {
    return function(input) {
      var pattern = /([a-z]{2}\-)((?:north|south|east|west)(?:east|west)?)\-(\d)/g;
      return input.replace(pattern, function(match, region, direction, regionNumber) {
        var result = [region.toUpperCase()];
        if (direction.indexOf('north') !== -1) {
          result.push('N');
        }
        if (direction.indexOf('south') !== -1) {
          result.push('S');
        }
        if (direction.indexOf('east') !== -1) {
          result.push('E');
        }
        if (direction.indexOf('west') !== -1) {
          result.push('W');
        }
        result.push(regionNumber);
        return result.join('');
      });
    };
  }
);
