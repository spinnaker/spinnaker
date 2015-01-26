'use strict';


angular
  .module('deckApp.config.controller', [
    'deckApp.applications.write.service',
    'deckApp.confirmationModal.service',
  ])
  .controller('ConfigController', function ($modal, $state, applicationWriter, confirmationModalService,  application) {
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

      var submitMethod = function() {
        return applicationWriter.deleteApplication(application.attributes);
      };

      var taskMonitor = {
        application: application,
        title: 'Deleting ' + application.name,
        hasKatoTask: false,
        onTaskComplete: function() {
          $state.go('home.applications');
        }
      };

      confirmationModalService.confirm({
        header: 'Really delete ' + application.name + '?',
        buttonText: 'Delete ' + application.name,
        destructive: true,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };

    return vm;

  });
