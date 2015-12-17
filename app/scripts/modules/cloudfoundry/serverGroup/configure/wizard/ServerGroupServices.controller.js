'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.cf.services.controller', [
        require('../../../../core/modal/wizard/modalWizard.service.js'),
    ])
    .controller('cfServerGroupServicesCtrl', function($scope, modalWizardService) {

        modalWizardService.getWizard().markComplete('services');

        $scope.$watch('form.$valid', function(newVal) {
            if (newVal) {
                modalWizardService.getWizard().markClean('services');
            } else {
                modalWizardService.getWizard().markDirty('services');
            }
        });

    });
