'use strict';

const angular = require('angular');

import { V2_MODAL_WIZARD_SERVICE } from '@spinnaker/core';

module.exports = angular.module('spinnaker.serverGroup.configure.cf.services.controller', [
        V2_MODAL_WIZARD_SERVICE,
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
