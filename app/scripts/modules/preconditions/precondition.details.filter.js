'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.precondition.details.filter', [])
  .filter('preconditionWhen', function() {
    return function(input, level) {

      input = input.replace('.', ' ').replace('pipeline', ( level === 'application' ? 'Any ' : 'This ' ) + 'pipeline is');
      input = input.replace('.', ' ').replace('stage', 'This stage is ');

      if(input.indexOf('failed')>-1){
        input = input.replace('pipeline is', 'pipeline has');
        input = input.replace('stage is', 'stage has');
      }

      return input;
    };
  }).filter('preconditionType', function() {
    return function(input) {
      return input.charAt(0).toUpperCase() + input.slice(1);
    };
  }).name;
