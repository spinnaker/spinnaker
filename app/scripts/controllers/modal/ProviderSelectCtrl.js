'use strict';


angular.module('deckApp')
  .controller('ProviderSelectCtrl', function($scope, $modalInstance, settings) {

    $scope.command = {
      provider: ''
    };

    $scope.providerOptions = settings.providers;

    if (!settings.providers || (settings.providers.length && settings.providers.length === 1)) {
      $modalInstance.resolve(settings.providers[0]);
    }

    this.selectProvider = function() {
      $modalInstance.close($scope.command.provider);
    };

    this.cancel = $modalInstance.dismiss;

  });
