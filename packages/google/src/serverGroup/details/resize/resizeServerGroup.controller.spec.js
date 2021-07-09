'use strict';

import { SERVER_GROUP_WRITER } from '@spinnaker/core';

const template = require('./resizeServerGroup.html');

// template dependencies
require('../../../common/footer.directive.html');

describe('Controller: gceResizeServerGroupCtrl', function () {
  let $controller, scope, gceAutoscalingPolicyWriter, serverGroupWriter, $compile, $templateCache, $q;

  beforeEach(
    window.module(
      require('./resizeServerGroup.controller').name,
      require('../../../autoscalingPolicy/autoscalingPolicy.write.service').name,
      SERVER_GROUP_WRITER,
    ),
  );

  // https://docs.angularjs.org/guide/migration#migrate1.5to1.6-ng-services-$compile
  beforeEach(
    window.module(($compileProvider) => {
      $compileProvider.preAssignBindingsEnabled(true);
    }),
  );

  beforeEach(
    window.inject(function (
      _$controller_,
      _$q_,
      _$compile_,
      _$templateCache_,
      $rootScope,
      _gceAutoscalingPolicyWriter_,
      _serverGroupWriter_,
    ) {
      scope = $rootScope.$new();
      gceAutoscalingPolicyWriter = _gceAutoscalingPolicyWriter_;
      serverGroupWriter = _serverGroupWriter_;
      $controller = _$controller_;
      $compile = _$compile_;
      $templateCache = _$templateCache_;
      $q = _$q_;
    }),
  );

  it('should instantiate the controller', function () {
    const controller = $controller('gceResizeServerGroupCtrl', {
      $scope: scope,
      $uibModalInstance: { result: { then: angular.noop } },
      application: {},
      serverGroup: {
        asg: {
          minSize: 0,
        },
      },
    });

    expect(controller).toBeDefined();
  });

  describe('behavior for server group with autoscaler', function () {
    let controller;
    beforeEach(function () {
      controller = $controller('gceResizeServerGroupCtrl', {
        $scope: scope,
        $uibModalInstance: {
          result: $q.resolve(),
        },
        application: {},
        serverGroup: {
          autoscalingPolicy: {
            minNumReplicas: 1,
            maxNumReplicas: 10,
          },
        },
      });

      $compile($templateCache.get(template))(scope);
      scope.$digest();
      spyOn(controller, 'isValid').and.returnValue(true);
    });

    it('controller.resize() should call gceAutoscalingPolicyWriter.upsertAutoscalingPolicy', function () {
      spyOn(serverGroupWriter, 'resizeServerGroup').and.callThrough();
      spyOn(gceAutoscalingPolicyWriter, 'upsertAutoscalingPolicy').and.callThrough();

      controller.resize();

      expect(serverGroupWriter.resizeServerGroup).not.toHaveBeenCalled();
      expect(gceAutoscalingPolicyWriter.upsertAutoscalingPolicy).toHaveBeenCalled();
    });
  });

  describe('behavior for server group without autoscaler', function () {
    let controller;
    beforeEach(function () {
      controller = $controller('gceResizeServerGroupCtrl', {
        $scope: scope,
        $uibModalInstance: {
          result: $q.resolve(),
        },
        application: {},
        serverGroup: {
          asg: {
            desiredCapacity: 10,
          },
        },
      });

      $compile($templateCache.get(template))(scope);
      scope.$digest();
      spyOn(controller, 'isValid').and.returnValue(true);
    });

    it('controller.resize() should call serverGroupWriter.resizeServerGroup', function () {
      spyOn(serverGroupWriter, 'resizeServerGroup').and.callThrough();
      spyOn(gceAutoscalingPolicyWriter, 'upsertAutoscalingPolicy').and.callThrough();
      controller.resize();

      expect(serverGroupWriter.resizeServerGroup).toHaveBeenCalled();
      expect(gceAutoscalingPolicyWriter.upsertAutoscalingPolicy).not.toHaveBeenCalled();
    });
  });
});
