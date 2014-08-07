'use strict';

require('../app');
var rx = require('rxjs');
var angular = require('angular');

angular.module('deckApp')
  .factory('RxService', function () {
    return rx;
  }
);
