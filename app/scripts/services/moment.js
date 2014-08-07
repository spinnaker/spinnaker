'use strict';

require('../app');
var moment = require('moment');
var angular = require('angular');

angular.module('deckApp')
  .factory('momentService', function() {
    return moment;
  });
