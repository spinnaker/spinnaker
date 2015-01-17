'use strict';


angular
  .module('deckApp.config.controller', [])
  .controller('ConfigController', function ($scope, $modal, application) {
    var vm = this;

    vm.editApplication = function() {
      $modal.open({
        templateUrl: 'scripts/modules/config/modal/editApplication.html',
        controller: 'EditApplicationController',
        controllerAs: 'editApp',
        resolve: {
          application: function () { return  application; }
        }
      });

    };

    return vm;

  });
