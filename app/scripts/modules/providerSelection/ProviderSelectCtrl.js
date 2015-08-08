'use strict';

let angular = require('angular');

require('./provider.image.service.provider.js');

module.exports = angular.module('spinnaker.providerSelection')
  .controller('ProviderSelectCtrl', function($scope, $modalInstance, settings, providerImageService, providerOptions) {

    $scope.command = {
      provider: ''
    };

    $scope.getImage = function(provider) {
      return providerImageService.getImage(provider, 'logo');
    };

    $scope.providerOptions = providerOptions;

    this.selectProvider = function() {
      $modalInstance.close($scope.command.provider);
    };

    this.cancel = $modalInstance.dismiss;

  }).name;
