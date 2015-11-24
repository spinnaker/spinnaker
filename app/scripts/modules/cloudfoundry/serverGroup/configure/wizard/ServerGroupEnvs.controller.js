'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.cf.envs.controller', [
        require('../../../../core/modal/wizard/modalWizard.service.js'),
    ])
    .controller('cfServerGroupEnvsCtrl', function($scope, modalWizardService) {

        modalWizardService.getWizard().markComplete('envs');

        $scope.$watch('form.$valid', function(newVal) {
            if (newVal) {
                modalWizardService.getWizard().markClean('envs');
            } else {
                modalWizardService.getWizard().markDirty('envs');
            }
        });

    }).name;
