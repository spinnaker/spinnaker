'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.application.config.delete.directive', [
    require('angular-ui-router'),
    require('./../service/applications.write.service.js'),
    require('../../confirmationModal/confirmationModal.service.js'),

  ])
  .directive('deleteApplicationSection', function (overrideRegistry) {
    return {
      restrict: 'E',
      templateUrl: overrideRegistry.getTemplate('deleteApplicationSectionDirective', require('./deleteApplicationSection.directive.html')),
      scope: {},
      bindToController: {
        application: '=',
      },
      controllerAs: 'vm',
      controller: 'DeleteApplicationSectionCtrl',
    };
  })
  .controller('DeleteApplicationSectionCtrl', function ($state, applicationWriter, confirmationModalService) {
    if (this.application.notFound) {
      return;
    }

    this.serverGroupCount = this.application.serverGroups.data.length;
    this.hasServerGroups = Boolean(this.serverGroupCount);

    this.deleteApplication = () => {

      var submitMethod = () => {
        return applicationWriter.deleteApplication(this.application.attributes);
      };

      var taskMonitor = {
        application: this.application,
        title: 'Deleting ' + this.application.name,
        hasKatoTask: false,
        onTaskComplete: () => {
          $state.go('home.infrastructure');
        }
      };

      confirmationModalService.confirm({
        header: 'Really delete ' + this.application.name + '?',
        buttonText: 'Delete ' + this.application.name,
        provider: 'aws',
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };
  });
