'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.createLoadBalancer.executionDetails.controller', [
  require('angular-ui-router'),
  require('../../../../delivery/details/executionDetailsSection.service.js'),
  require('../../../../delivery/details/executionDetailsSectionNav.directive.js'),
  require('../../../../cloudProvider/cloudProvider.registry.js'),
])
  .controller('createLoadBalancerExecutionDetailsCtrl', function ($scope, $stateParams, $timeout, cloudProviderRegistry,
                                                                  executionDetailsSectionService) {

    $scope.configSections = ['loadBalancerConfig', 'taskStatus'];

    function initialize() {
      executionDetailsSectionService.synchronizeSection($scope.configSections);
      $scope.detailsSection = $stateParams.details;

      // When this is called from a stateChangeSuccess event, the stage in the scope is not updated in this digest cycle
      // so we need to wait until the next cycle to update the created artifacts
      $timeout(function () {
        var context = $scope.stage.context || {},
          results = [];

        function addCreatedArtifacts(key) {
          var createdArtifacts = resultObjects;
          if (createdArtifacts) {
            createdArtifacts.forEach(function (artifact) {
              _.forEach(artifact[key], function (valueObj, key) {
                var result = {
                  type: 'loadBalancers',
                  application: context.application,
                  name: valueObj.name,
                  region: key,
                  account: context.account,
                  dnsName: valueObj.dnsName,
                  provider: context.providerType || context.cloudProvider || 'aws'
                };
                results.push(result);
              });
            });
          }
        }

        if (context && context['kato.tasks'] && context['kato.tasks'].length) {
          var resultObjects = context['kato.tasks'][0].resultObjects;
          if (resultObjects && resultObjects.length) {
            results = [];
            addCreatedArtifacts('loadBalancers');
          }
        }
        $scope.createdLoadBalancers = results;
        $scope.provider = context.cloudProvider || context.providerType || 'aws';
      });

    }

    $scope.hasSubnetDeployments = () => {
      let cloudProvider = $scope.provider || 'aws';
      return cloudProviderRegistry.hasValue(cloudProvider, 'subnet');
    };

    initialize();

    $scope.$on('$stateChangeSuccess', initialize, true);

  });
