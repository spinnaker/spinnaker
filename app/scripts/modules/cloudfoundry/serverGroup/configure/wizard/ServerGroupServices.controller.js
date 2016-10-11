'use strict';

import modalWizardServiceModule from 'core/modal/wizard/v2modalWizard.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.cf.services.controller', [
        modalWizardServiceModule,
    ])
    .controller('cfServerGroupServicesCtrl', function(/*$scope, v2modalWizardService*/) {

        // TODO(GLT): Fix roles after Find/Bake updates are rolled in.

        //v2modalWizardService.markComplete('services');
        //
        //$scope.$watch('form.$valid', function(newVal) {
        //    if (newVal) {
        //        v2modalWizardService.markClean('services');
        //    } else {
        //        v2modalWizardService.markDirty('services');
        //    }
        //});

    });
