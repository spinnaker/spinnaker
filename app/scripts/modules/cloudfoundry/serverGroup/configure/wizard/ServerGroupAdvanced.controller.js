'use strict';

import {V2_MODAL_WIZARD_SERVICE} from 'core/modal/wizard/v2modalWizard.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.cf.advanced.controller', [
        V2_MODAL_WIZARD_SERVICE,
    ])
    .controller('cfServerGroupAdvancedCtrl', function(/*$scope, v2modalWizardService*/) {

        // TODO(GLT): Fix roles after Find/Bake updates are rolled in.

        //v2modalWizardService.markComplete('advanced');
        //
        //$scope.$watch('form.$valid', function(newVal) {
        //    if (newVal) {
        //        v2modalWizardService.markClean('advanced');
        //    } else {
        //        v2modalWizardService.markDirty('advanced');
        //    }
        //});

    });
