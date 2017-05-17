'use strict';

const angular = require('angular');

import { V2_MODAL_WIZARD_SERVICE } from '@spinnaker/core';

module.exports = angular.module('spinnaker.serverGroup.configure.cf.loadBalancers.controller', [
        V2_MODAL_WIZARD_SERVICE,
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
