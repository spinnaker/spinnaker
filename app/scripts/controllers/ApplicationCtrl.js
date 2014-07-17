'use strict';

angular.module('deckApp')
  .controller('ApplicationCtrl', function($scope, application) {

    $scope.view = {
      activeTab: 'clusters'
    };

    $scope.application = application;



  });
