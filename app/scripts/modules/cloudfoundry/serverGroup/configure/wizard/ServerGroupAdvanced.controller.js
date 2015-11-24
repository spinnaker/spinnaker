'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.cf.advanced.controller', [
        require('../../../../core/modal/wizard/modalWizard.service.js'),
    ])
    .controller('cfServerGroupAdvancedCtrl', function($scope, modalWizardService) {

        modalWizardService.getWizard().markComplete('advanced');

        $scope.$watch('form.$valid', function(newVal) {
            if (newVal) {
                modalWizardService.getWizard().markClean('advanced');
            } else {
                modalWizardService.getWizard().markDirty('advanced');
            }
        });

    }).name;
