'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.applicationProperties.controller', [
    require('angular-ui-router'),
    require('./fastProperty.read.service.js'),
    require('./fastProperty.write.service.js'),
    require('../../core/confirmationModal/confirmationModal.service.js'),
    require('./fastPropertyTransformer.service.js'),
    require('../../core/application/service/applications.read.service'),
    require('../../core/utils/lodash.js'),
  ])
  .controller('ApplicationPropertiesController', function ($scope, $filter, $uibModal, $state, app, applicationReader,
                                                           fastPropertyReader, fastPropertyWriter, fastPropertyTransformer, _) {
    var vm = this;
    const application = app;


    let refreshApp = () => {
      app.refreshImmediately(true);
    };

    vm.application = app;
    vm.app = application.name;
    vm.itemsPerPage = 25;
    vm.filterString = '';
    vm.promotionStateFilter = '';
    vm.pagination = {
      currentPage : 1,
      maxSize: 10,
      itemsPerPage : vm.itemsPerPage,
    };

    vm.promotionPaneOpen = true;

    vm.openRolloutDetailsList = [];

    vm.isRolloutDetailsOpen = function(id) {
      var idIndex = vm.openRolloutDetailsList.indexOf(id);
      return idIndex > -1;
    };

    vm.toggleRolloutDetails = function(promotion){
      var idIndex = vm.openRolloutDetailsList.indexOf(promotion.id);
      if(vm.isRolloutDetailsOpen(promotion.id)) {
        vm.openRolloutDetailsList.splice(idIndex, 1);
      } else {
        vm.openRolloutDetailsList.push(promotion.id);
      }
    };


    vm.togglePromotionPane = function() {
      vm.promotionPaneOpen = !vm.promotionPaneOpen;
    };

    function sortProperties(properties) {
      return $filter('orderBy')(properties, 'key');
    }

    vm.resultsPage = function(properties) {
      if(properties) {
        var start = (vm.pagination.currentPage - 1) * vm.itemsPerPage;
        var end = vm.pagination.currentPage * vm.itemsPerPage;
        return properties.slice(start, end);
      }
      return [];
    };


    vm.filterProperties = function() {
      return $filter('anyFieldFilter') (vm.properties,  {key: vm.filterString, value: vm.filterString});
    };

    vm.updateStateFilter = function(state) {
      if(state) {
        vm.filteredPromotions = vm.promotions.filter(function(promotion) {
          return promotion.state === state;
        });
      } else {
        vm.filteredPromotions = vm.promotions;
      }

    };

    vm.filteredResultPage = function() {
      return vm.resultsPage(vm.filterProperties());
    };

    vm.setFilteredProperties = function() {
      vm.filteredProps = vm.filterProperties();
      vm.filteredPage = vm.filteredResultPage();
    };

    vm.delete = function(property) {
      $uibModal.open({
        templateUrl: require('./modal/deleteFastProperty.html'),
        controller: 'DeleteFastPropertyModalController',
        controllerAs: 'delete',
        resolve: {
          fastProperty: function() {
            return property;
          }
        }
      }).result.then(refreshApp);
    };

    vm.editFastProperty = function(property) {
      $uibModal.open({
        templateUrl: require('./modal/wizard/fastPropertyWizard.html'),
        controller: 'FastPropertyUpsertController',
        controllerAs: 'newFP',
        resolve: {
          clusters: function() {return application.clusters; },
          appName: function() {return application.name; },
          isEditing: function() {return true; },
          applicationList: function(applicationReader) {
            return applicationReader.listApplications();
          },
          fastProperty: function() {
            var propertyWithScope = fastPropertyWriter.extractScopeIntoSelectedScope(property);
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


    vm.newFastPropertyModal = function() {
      $uibModal.open({
        templateUrl: require('./modal/wizard/fastPropertyWizard.html'),
        controller: 'FastPropertyUpsertController',
        controllerAs: 'newFP',
        resolve: {
          clusters: function() {return application.clusters; },
          appName: function() {return application.name; },
          isEditing: function() {return false; },
          fastProperty: function() {return {}; },
          applicationList: function(applicationReader) {
            return applicationReader.listApplications();
          }
        }
      }).result.then(routeToApplication);
    };


    vm.appInstanceList = [];
    vm.getAppInstnaceList = () => {
      if(vm.appInstanceList.length > 0) {
        return vm.appInstanceList;
      }
      vm.appInstanceList = _.chain(app)
                            .get('clusters').flatten()
                            .map('serverGroups').flatten()
                            .map('instances').flatten()
                            .map('id').flatten()
                            .value();
      return vm.appInstanceList;
    };

    vm.appAsgList = [];
    vm.getAppAsgList = () => {
      if(vm.appAsgList.length > 0) {
        return vm.appAsgList;
      }

      vm.appAsgList = _.chain(app)
                      .get('clusters').flatten()
                      .map('serverGroups').flatten()
                      .value();

      return vm.appAsgList;
    };

    vm.appClusterList = [];
    vm.getAppClusterList = () => {
      if (vm.appClusterList.length > 0) {
        return vm.appClusterList;
      }

      vm.appClusterList = _.chain(app)
                          .get('clusters').flatten()
                          .map('name').flatten()
                          .value();

      return vm.appClusterList;

    };


    let validateScopeProperty = (property, propName, getListFn) => {
      if(property[propName])  {
        let list = getListFn();
        let inList = list.some( (i) => {
          return i === property[propName];
        });
        property.isValid = _.has(property.isValid) && !property.isValid ? false : inList;

        if( !inList ) {
          if(Array.isArray(property.errors)) {
            property.errors.push(propName);
          } else {
            property.errors = [propName];
          }
        }
      } else {
        property.isValid = _.has(property.isValid) ? property.isValid : true;
      }
    };

    vm.auditFastPropertyList = (fpList) => {
      fpList.forEach( (prop) => {
        validateScopeProperty(prop, 'serverId', vm.getAppInstnaceList);
        validateScopeProperty(prop, 'asg', vm.getAppAsgList);
        validateScopeProperty(prop, 'cluster', vm.getAppClusterList);
      });

      return fpList;
    };


    function fetchFastProperties() {
      fastPropertyReader.fetchForAppName(application.name)
        .then( (data) => {
          var list = data.propertiesList || [];
          vm.properties = sortProperties(list);
          return vm.properties;
        })
        .then(vm.auditFastPropertyList)
        .then(vm.setFilteredProperties);
    }

    function loadPromotions() {
      fastPropertyReader.loadPromotionsByApp(application.name)
        .then(function(promotionList) {
          vm.promotions = vm.filteredPromotions = promotionList;
          return vm.promotions;
        })
        .then(fastPropertyTransformer.sortRunningPromotionsFirst)
        .then(function(sortedPromotions) {
          vm.promotions = sortedPromotions;
        })
        .then(function() {
          vm.updateStateFilter(vm.promotionStateFilter);
        })
        .catch(function(error){
        console.warn(error);
      });

    }

    function routeToApplication() {
      $state.go(
        'home.applications.application.properties', {
          application: application.name
        }
      );
    }


    application.registerAutoRefreshHandler(fetchFastProperties, $scope);
    application.registerAutoRefreshHandler(loadPromotions, $scope);

    fetchFastProperties();
    loadPromotions();

    return vm;
  });
