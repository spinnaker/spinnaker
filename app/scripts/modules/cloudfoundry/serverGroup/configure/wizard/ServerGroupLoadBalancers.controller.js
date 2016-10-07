'use strict';

import modalWizardServiceModule from '../../../../core/modal/wizard/v2modalWizard.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.cf.loadBalancers.controller', [
        modalWizardServiceModule,
    ])
    .controller('cfServerGroupLoadBalancersCtrl', function(/*$scope, v2modalWizardService*/) {

        // TODO(GLT): Fix roles after Find/Bake updates are rolled in.

        //v2modalWizardService.markComplete('loadBalancers');
        //
        //$scope.$watch('form.$valid', function(newVal) {
        //    if (newVal) {
        //        v2modalWizardService.markClean('loadBalancers');
        //    } else {
        //        v2modalWizardService.markDirty('loadBalancers');
        //    }
        //});

    });
