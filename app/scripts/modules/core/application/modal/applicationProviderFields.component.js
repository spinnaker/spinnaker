'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.application.modal.applicationProviderFields.directive', [
    require('../../cloudProvider/cloudProvider.registry'),
  ])
  .component('applicationProviderFields', {
      templateUrl: require('./applicationProviderFields.component.html'),
      bindings: {
        application: '=',
        cloudProviders: '=',
      },
      controller: 'ApplicationProviderFieldsCtrl',
  })
  .controller('ApplicationProviderFieldsCtrl', function(cloudProviderRegistry) {
    const templateUrlPath = 'applicationProviderFields.templateUrl';

    this.getRelevantProviderFieldsTemplates = () => {
      let candidateProvidersToShow;

      if (this.application.cloudProviders.length === 0) {
        candidateProvidersToShow = this.cloudProviders;
      } else {
        candidateProvidersToShow = this.application.cloudProviders;
      }

      return candidateProvidersToShow
        .filter(provider => cloudProviderRegistry.hasValue(provider, templateUrlPath))
        .map(provider => cloudProviderRegistry.getValue(provider, templateUrlPath));
    };
  });
