'use strict';

require('../app');
var jquery = require('jquery');
var angular = require('angular');

angular.module('deckApp')
  .factory('$', function() {
    return jquery;
  });
