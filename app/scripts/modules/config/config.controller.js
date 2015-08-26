'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.config.controller', [
    require('../applications/applications.write.service.js'),
    require('../confirmationModal/confirmationModal.service.js'),
    require('../caches/cacheInitializer.js'),
    require('../caches/infrastructureCaches.js'),
    require('utils/lodash.js'),
  ])
  .controller('ConfigController', function ($modal, $state, $log, applicationWriter, confirmationModalService,
                                            cacheInitializer, infrastructureCaches, app, _) {
    const application = app;
    var vm = this;

    vm.serverGroupCount = application.serverGroups.length;
    vm.hasServerGroups = Boolean(vm.serverGroupCount);
    vm.error = '';

    vm.editApplication = function () {
      $modal.open({
        templateUrl: require('./modal/editApplication.html'),
        controller: 'EditApplicationController',
        controllerAs: 'editApp',
        resolve: {
          application: function () {
            return application;
          }
        }
      }).result.then(function(newAttributes) {
          application.attributes = newAttributes;
        });
    };

    vm.deleteApplication = function () {

      var submitMethod = function () {
        return applicationWriter.deleteApplication(application.attributes);
      };

      var taskMonitor = {
        application: application,
        title: 'Deleting ' + application.name,
        hasKatoTask: false,
        onTaskComplete: function () {
          $state.go('home.applications');
        }
      };

      confirmationModalService.confirm({
        header: 'Really delete ' + application.name + '?',
        buttonText: 'Delete ' + application.name,
        destructive: true,
        provider: 'aws',
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod
      });
    };

    vm.refreshCaches = function () {
      vm.clearingCaches = true;
      cacheInitializer.refreshCaches().then(
        function () {
          vm.clearingCaches = false;
        },
        function (e) {
          $log.error('Error refreshing caches:', e);
          vm.clearingCaches = false;
        });
    };

    vm.getCacheInfo = function (cache) {
      return infrastructureCaches[cache].getStats();
    };

    vm.refreshCache = function (key) {
      vm.clearingCache = vm.clearingCache || {};
      vm.clearingCache[key] = true;
      cacheInitializer.refreshCache(key).then(
        function () {
          vm.clearingCache[key] = false;
        },
        function (e) {
          $log.error('Error refreshing caches:', e);
          vm.clearingCaches = false;
        }
      );
    };

    return vm;

  }).name;
