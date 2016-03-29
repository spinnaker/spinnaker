'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.fastProperties.details.controller', [
    require('angular-ui-router'),
    require('./fastProperty.read.service'),
    require('./fastProperty.write.service'),

  ])
  .controller('FastPropertiesDetailsController', function($scope, $state, $uibModal, fastProperty, fastPropertyReader, fastPropertyWriter) {

    let vm = this;
    vm.application = $scope.application;

    let extractEnvFromId = (propertyId) => {
      let list = propertyId.split('|');
      return list[2] || 'prod';
    };

    let getProperty = (environment) => {
      let env = environment || extractEnvFromId(fastProperty.propertyId);
      fastPropertyReader.getPropByIdAndEnv(fastProperty.propertyId, env)
        .then((results) => {
          vm.property = results.property;
        })
        .catch(() => {
            let otherEnv = env === 'prod' ? 'test' : 'prod';
            getProperty(otherEnv);
        });
    };

    vm.editFastProperty = function() {
      $uibModal.open({
        templateUrl: require('./modal/wizard/fastPropertyWizard.html'),
        controller: 'FastPropertyUpsertController',
        controllerAs: 'newFP',
        resolve: {
          clusters: function() {return vm.application.clusters; },
          appName: function() {return vm.application.name; },
          isEditing: function() {return true; },
          applicationList: function(applicationReader) {
            return applicationReader.listApplications();
          },
          fastProperty: function() {
            var propertyWithScope = fastPropertyWriter.extractScopeIntoSelectedScope(vm.property);
            return fastPropertyReader.fetchImpactCountForScope(propertyWithScope.selectedScope)
              .then( function(impact) {
                propertyWithScope.impactCount = impact.count;
                return propertyWithScope;
              }, function() {
                propertyWithScope.impactCount = '?';
                return propertyWithScope;
              });
          },
        }

      }).result.then(routeToApplication);
    };

    function routeToApplication() {
      $state.go(
        'home.applications.application.properties', {
          application: vm.application.name
        }
      );
    }

    let refreshApp = () => angular.noop;

    vm.delete = function() {
      $uibModal.open({
        templateUrl: require('./modal/deleteFastProperty.html'),
        controller: 'DeleteFastPropertyModalController',
        controllerAs: 'delete',
        resolve: {
          fastProperty: function() {
            return vm.property;
          }
        }
      }).result.then(refreshApp);
    };

    getProperty();
  });
