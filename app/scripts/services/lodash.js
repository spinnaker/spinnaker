'use strict';

require('../app');
var lodash = require('lodash');
var angular = require('angular');

angular.module('deckApp')
  .factory('_', function() {
    return lodash;
  });
