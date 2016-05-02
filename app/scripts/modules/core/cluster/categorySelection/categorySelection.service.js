'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.categorySelection.service', [
  require('../../account/account.service.js'),
  require('../../config/settings.js'),
  require('../../utils/lodash.js'),
])
  .factory('categorySelectionService', function($uibModal, $q, _, accountService, settings) {
    function availableCategories() {
      var available = [ { name: 'serverGroup', display: 'Server Group', },
      ];

      if (settings.feature.jobs) {
        available.push({ name: 'job', display: 'Job', });
      }

      return available;
    }

    function selectCategory() {
        var categories = availableCategories();
        var category;

        if (categories.length > 1) {
          category = $uibModal.open({
            templateUrl: require('./categorySelection.html'),
            controller: 'CategorySelectCtrl as ctrl',
            resolve: {
              categoryOptions: function() { return categories; }
            }
          }).result;
        } else if (categories.length === 1) {
          category = $q.when(categories[0].name);
        } else {
          category = $q.when(settings.defaultCategory || 'serverGroup');
        }
        return category;
    }

    return {
      selectCategory: selectCategory,
      availableCategories: availableCategories,
    };

  }).controller('CategorySelectCtrl', function ($scope, $uibModalInstance, categorySelectionService) {
    $scope.command = {
      category: 'serverGroup'
    };

    $scope.categoryOptions = categorySelectionService.availableCategories();

    this.selectCategory = function() {
      $uibModalInstance.close($scope.command.category);
    };

    this.cancel = $uibModalInstance.dismiss;

  });
