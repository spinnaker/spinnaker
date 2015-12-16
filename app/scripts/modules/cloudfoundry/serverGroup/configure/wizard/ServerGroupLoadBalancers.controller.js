'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.cf.loadBalancers.controller', [
        require('../../../../core/modal/wizard/modalWizard.service.js'),
    ])
    .controller('cfServerGroupLoadBalancersCtrl', function($scope, modalWizardService) {

        modalWizardService.getWizard().markComplete('loadBalancers');

        $scope.$watch('form.$valid', function(newVal) {
            if (newVal) {
                modalWizardService.getWizard().markClean('loadBalancers');
            } else {
                modalWizardService.getWizard().markDirty('loadBalancers');
            }
        });

    });
