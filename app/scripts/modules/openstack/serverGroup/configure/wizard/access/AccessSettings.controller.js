'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.openstack.accessSettings', [
  require('angular-ui-router'),
  require('angular-ui-bootstrap'),
  require('../../../../loadBalancer/loadBalancerSelectField.directive.js'),
]).controller('openstackServerGroupAccessSettingsCtrl', function($scope, securityGroupReader, _, v2modalWizardService, $rootScope, infrastructureCaches, cacheInitializer) {

  this.refreshTooltipTemplate = require('../../../../common/refresh.tooltip.html');
  $scope.$ctrl = {
    label: 'Security Groups'
  };

  var currentRequestId = 0;
  var updatingOptions = false;
  var coveredThreshold = 0;

  function updateSecurityGroups() {
    currentRequestId++;
    var requestId = currentRequestId;
    updatingOptions = true;

    securityGroupReader.loadSecurityGroups().then(function(allSecurityGroups) {
      if (requestId !== currentRequestId) {
        return;
      }

      $scope.securityGroups = [];

      var indexedGroups = {};
      if( $scope.command.credentials && $scope.command.region && allSecurityGroups[$scope.command.credentials] ) {
        indexedGroups = allSecurityGroups[$scope.command.credentials][$scope.command.region] || {};
        $scope.securityGroups = _(indexedGroups).values().sortBy(function(sg) { return sg.name; }).uniq().value();
      }

      //remove non-existent groups from the list
      $scope.command.securityGroups = _.intersection($scope.command.securityGroups || [], _.map($scope.securityGroups, function(sg) { return sg.id; }));

      coveredThreshold = infrastructureCaches['securityGroups'].getStats().ageMax;
      updatingOptions = false;
    }, function() {
      if (requestId === currentRequestId) {
        coveredThreshold = infrastructureCaches['securityGroups'].getStats().ageMax;
        updatingOptions = false;
      }
    });
  }

  function resetRefreshingFlag() {
    $scope.refreshing = false;
  }

  this.refreshSecurityGroups = function() {
    $scope.refreshing = true;
    return cacheInitializer.refreshCache('securityGroups').then(resetRefreshingFlag, resetRefreshingFlag);
  };

  var stopWatchingRefreshTime = $rootScope.$watch(function() { return infrastructureCaches['securityGroups'].getStats().ageMax; }, function(ageMax) {
    if (ageMax) {
      $scope.$ctrl.lastRefresh = ageMax;

      //update options, but don't start an infinite loop since fetching the options might also update ageMax
      if( !updatingOptions && ageMax > coveredThreshold ) {
        updateSecurityGroups();
      }
    }
  });

  $scope.$on('$destroy', stopWatchingRefreshTime);


  updateSecurityGroups();
  $scope.$watch('command.credentials', updateSecurityGroups);
  $scope.$watch('command.region', updateSecurityGroups);

  $scope.$watch('accessSettings.$valid', function(newVal) {
    if (newVal) {
      v2modalWizardService.markClean('access-settings');
      v2modalWizardService.markComplete('access-settings');
    } else {
      v2modalWizardService.markIncomplete('access-settings');
    }
  });

});
