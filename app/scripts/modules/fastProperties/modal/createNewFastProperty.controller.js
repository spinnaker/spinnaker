'use strict';

let angular = require('angular');

// newFP
module.exports = angular
  .module('spinnaker.newFastProperty.controller', [
    require('../fastProperty.write.service.js'),
    require('utils/lodash.js'),
  ])
  .controller('CreateFastPropertyModalController', function($modalInstance, clusters, appName, fastProperty, fastPropertyWriter, isEditing, _) {
    var vm = this;

    vm.isEditing = isEditing || false;
    vm.property = fastProperty;
    vm.property.env = 'test';
    vm.heading = vm.isEditing ? 'Update Fast Property' : 'Create Fast Property';
    vm.startScope = {};

    vm.clusters = clusters;
    vm.appName = appName;

    vm.submit = function() {
      fastPropertyWriter.upsertFastProperty(vm.property).then(
        function(result) {
          $modalInstance.close(result);
        },
        function(error) {
          window.alert(JSON.stringify(error));
        });
    };

    vm.update = function() {
      var updatedParams = _(vm.property).omit(['ts', 'createdAsCanary']).value();
      fastPropertyWriter.upsertFastProperty(updatedParams).then(
        function(result) {
          $modalInstance.close(result);
        }
      );
    };

    return vm;
  });
