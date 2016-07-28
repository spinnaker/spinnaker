'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.application.config.serialize.component', [
    require('angular-ui-router'),
    require('./../../serialize/serialize.write.service.js'),
    require('../../confirmationModal/confirmationModal.service.js'),
    require('../../overrideRegistry/override.registry.js'),
  ])
  .component('serializeApplicationSection', {
    templateUrl: require('./serializeApplicationSection.component.html'),
    bindings: {
      application: '=',
    },
    controller: function ($state, serializeWriter, confirmationModalService) {
      if (this.application.notFound) {
        return;
      }

      this.serializeApplication = () => {

        var submitMethod = () => {
          return serializeWriter.serializeApplication(this.application.attributes);
        };

        var taskMonitor = {
          application: this.application,
          title: 'Serializing ' + this.application.name,
          hasKatoTask: true,
          onTaskComplete: () => {
            $state.go('home.infrastructure');
          }
        };

        confirmationModalService.confirm({
          header: 'Are you sure you want to serialize: ' + this.application.name + '?',
          buttonText: 'Serialize',
          provider: 'gce',
          taskMonitorConfig: taskMonitor,
          submitMethod: submitMethod
        });
      };
    }
  });
