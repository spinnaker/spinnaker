'use strict';

require('../app');
var rx = require('rx');
var angular = require('angular');

angular.module('deckApp')
  .factory('RxService', function () {
    return rx;
  }
);
