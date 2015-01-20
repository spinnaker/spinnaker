'use strict';


angular
  .module('deckApp.config.controller', [
    'deckApp.applications.write.service'
  ])
  .controller('ConfigController', function ($modal, $state, applicationWriter, application) {
    var vm = this;
    vm.serverGroupCount = application.serverGroups.length;
    vm.hasServerGroups = Boolean(vm.serverGroupCount);
    vm.error = '';

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

    vm.deleteApplication = function() {
      return applicationWriter
        .deleteApplication(application.attributes).then(
          returnToApplicationsList,
          showErrors
        );
    };

    function returnToApplicationsList() {
      $state.go('home.applications');
    }

    function showErrors(error) {
      vm.error = error.message;
    }

    return vm;

  });
