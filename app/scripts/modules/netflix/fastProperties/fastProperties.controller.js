'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.fastProperties.controller', [
    require('../../core/application/service/applications.read.service.js'),
    require('../../core/cache/deckCacheFactory.js'),
  ])
  .controller('FastPropertiesController', function ($filter, applicationReader, settings) {
    var vm = this;

    vm.isOn = settings.feature.fastProperties;

    vm.applicationsLoaded = false;

    vm.sortModel = { key: 'name' };

    vm.applicationFilter = '';

    vm.filterObject = {
      name: vm.applicationFilter,
    };

    vm.pagination = {
      currentPage: 1,
      itemsPerPage: 12,
      maxSize: 12
    };

    vm.filteredResultPage = function() {
      return vm.resultPage(vm.filter(vm.applications));
    };

    vm.resultPage = function(applications) {
      var start = (vm.pagination.currentPage - 1) * vm.pagination.itemsPerPage;
      var end = vm.pagination.currentPage * vm.pagination.itemsPerPage;
      return applications.slice(start, end);
    };

    vm.filter = function(applications) {
      return sortApplications(filterApplications(applications), vm.sortModel.key);
    };

    vm.filteredCount = function() {
      return filterApplications(vm.applications).length;
    };

    var filterApplications = function(applications) {
      var filtered = $filter('anyFieldFilter')(applications, {name: vm.applicationFilter});
      return filtered;
    };

    var sortApplications = function (applicationList, orderKey) {
      var sorted = $filter('orderBy')(applicationList, orderKey);
      return sorted;
    };

    applicationReader.listApplications().then(function(applications) {
      vm.applicationsLoaded = true;
      vm.applications = applications;
    });

    return vm;
  }).name;
