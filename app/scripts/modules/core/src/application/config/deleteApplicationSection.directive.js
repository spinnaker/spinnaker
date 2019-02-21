'use strict';

const angular = require('angular');

import { ApplicationWriter } from 'core/application/service/ApplicationWriter';
import { CONFIRMATION_MODAL_SERVICE } from 'core/confirmationModal/confirmationModal.service';
import { OVERRIDE_REGISTRY } from 'core/overrideRegistry/override.registry';

module.exports = angular
  .module('spinnaker.core.application.config.delete.directive', [
    require('@uirouter/angularjs').default,
    CONFIRMATION_MODAL_SERVICE,
    OVERRIDE_REGISTRY,
  ])
  .directive('deleteApplicationSection', function(overrideRegistry) {
    return {
      restrict: 'E',
      templateUrl: overrideRegistry.getTemplate(
        'deleteApplicationSectionDirective',
        require('./deleteApplicationSection.directive.html'),
      ),
      scope: {},
      bindToController: {
        application: '=',
      },
      controllerAs: 'vm',
      controller: 'DeleteApplicationSectionCtrl',
    };
  })
  .controller('DeleteApplicationSectionCtrl', ['$state', 'confirmationModalService', function($state, confirmationModalService) {
    if (this.application.notFound) {
      return;
    }

    this.serverGroupCount = this.application.serverGroups.data.length;
    this.hasServerGroups = Boolean(this.serverGroupCount);

    this.deleteApplication = () => {
      var submitMethod = () => ApplicationWriter.deleteApplication(this.application.attributes);

      var taskMonitor = {
        application: this.application,
        title: 'Deleting ' + this.application.name,
        hasKatoTask: false,
        onTaskComplete: () => {
          $state.go('home.infrastructure');
        },
      };

      confirmationModalService.confirm({
        header: 'Really delete ' + this.application.name + '?',
        buttonText: 'Delete ' + this.application.name,
        provider: 'aws',
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod,
      });
    };
  }]);
