'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.applicationProperties.controller', [
    require('./fastProperty.read.service.js'),
    require('./fastProperty.write.service.js'),
    require('./modal/createNewFastProperty.controller.js'),
    require('../confirmationModal/confirmationModal.service.js'),
    require('./fastPropetyTrasnformer.service.js'),
    require('utils/lodash.js'),
  ])
  .controller('ApplicationPropertiesController', function ($scope, $filter, $modal, $state, application, fastPropertyReader, fastPropertyWriter, fastPropertyTransformer, _) {
    var vm = this;

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


    vm.getToggleButtonName = function() {
      if(vm.promotionPaneOpen) {
        return 'Close';
      } else {
        return 'Open';
      }
    };

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
      $modal.open({
        templateUrl: require('./modal/deleteFastProperty.html'),
        controller: 'DeleteFastPropertyModalController',
        controllerAs: 'delete',
        resolve: {
          fastProperty: function() {
            return property;
          }
        }
      });
    };

    vm.editFastProperty = function(property) {
      $modal.open({
        templateUrl: require('./modal/newFastProperty.html'),
        controller: 'CreateFastPropertyModalController',
        controllerAs: 'newFP',
        resolve: {
          clusters: function() {return application.clusters; },
          appName: function() {return application.name; },
          isEditing: function() {return true; },
          fastProperty: function() {
            var propertyWithScope = fastPropertyWriter.extractScopeIntoSelectedScope(property);
            return fastPropertyReader.fetchImpactCountForScope(propertyWithScope.selectedScope).then(function(impact) {
              propertyWithScope.impactCount = impact.count || 0;
              return propertyWithScope;
            });
          },
        }

      }).result.then(routeToApplication);
    };


    vm.newFastPropertyModal = function() {
      $modal.open({
        templateUrl: require('./modal/newFastProperty.html'),
        controller: 'CreateFastPropertyModalController',
        controllerAs: 'newFP',
        resolve: {
          clusters: function() {return application.clusters; },
          appName: function() {return application.name; },
          isEditing: function() {return false; },
          fastProperty: function() {return {}; },
        }
      }).result.then(routeToApplication);
    };

    vm.continue = function($event, promotion) {
      $event.stopPropagation();
      promotion.isPromoting = true;
      fastPropertyWriter.continuePromotion(promotion.id);
    };

    vm.stop= function($event, promotion) {
      $event.stopPropagation();
      promotion.isPromoting = true;
      fastPropertyWriter.stopPromotion(promotion.id);
    };

    vm.getLastMessage = function(promotion) {
      if(promotion.history.length > 0) {
        return _(promotion.history).last().message;
      } else {
        return 'no history';
      }
    };

    function fetchFastProperties() {
      fastPropertyReader.fetchForAppName(application.name)
        .then(
        function(data) {
          var list = data.propertiesList || [];
          vm.properties = sortProperties(list);
        }
      )
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
  }).name;
