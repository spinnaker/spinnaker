'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('ApplicationCtrl', function($scope, application) {
    $scope.application = application;
  }
);

