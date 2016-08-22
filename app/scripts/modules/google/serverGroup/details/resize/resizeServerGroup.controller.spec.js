'use strict';

let template = require('./resizeServerGroup.html');

// template dependencies
require('../../../../core/task/monitor/taskMonitor.html');
require('../../../../core/task/modal/reason.directive.html');
require('../../../../core/modal/buttons/modalClose.directive.html');
require('../../../common/footer.directive.html');

describe('Controller: gceResizeServerGroupCtrl', function () {

  let $controller, scope, gceAutoscalingPolicyWriter, serverGroupWriter,
    $compile, $templateCache, $q;

  beforeEach(
    window.module(
      require('./resizeServerGroup.controller'),
      require('../../../autoscalingPolicy/autoscalingPolicy.write.service.js'),
      require('../../../../core/serverGroup/serverGroup.write.service.js')
    )
  );

  beforeEach(
    window.inject(function (_$controller_, _$q_, _$compile_, _$templateCache_,
                            $rootScope, _gceAutoscalingPolicyWriter_, _serverGroupWriter_) {

      scope = $rootScope.$new();
      gceAutoscalingPolicyWriter = _gceAutoscalingPolicyWriter_;
      serverGroupWriter = _serverGroupWriter_;
      $controller = _$controller_;
      $compile = _$compile_;
      $templateCache = _$templateCache_;
      $q = _$q_;
    })
  );

  it('should instantiate the controller', function () {
    let controller = $controller('gceResizeServerGroupCtrl', {
      $scope: scope,
      $uibModalInstance: {},
      application: {},
      serverGroup: {
        asg:{
          minSize:0
        }
      }
    });

    expect(controller).toBeDefined();
  });

  describe('behavior for server group with autoscaler', function () {
    let controller;
    beforeEach(function () {
      controller = $controller('gceResizeServerGroupCtrl', {
        $scope: scope,
        $uibModalInstance: {
          result: $q.resolve()
        },
        application: {},
        serverGroup: {
          autoscalingPolicy: {
            minNumReplicas: 1,
            maxNumReplicas: 10
          }
        }
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
          result: $q.resolve()
        },
        application: {},
        serverGroup: {
          asg: {
            desiredCapacity: 10
          }
        }
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


