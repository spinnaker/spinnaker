'use strict';

/**
 * @ngdoc function
 * @name deckApp.controller:MainCtrl
 * @description
 * # MainCtrl
 * Controller of the deckApp
 */
angular.module('deckApp')
  .controller('MainCtrl', function ($scope) {
    $scope.awesomeThings = [
      'HTML5 Boilerplate',
      'AngularJS',
      'Karma'
    ];
  });
