'use strict';

describe('Controller: ConfirmationModal', function () {
  var controller, accountService, taskMonitorService, params, $scope, $q, modalInstance;

  beforeEach(
    window.module(
      require('./confirmationModal.controller.js')
    )
  );

  beforeEach(window.inject(function($controller, $rootScope, _taskMonitorService_, _accountService_, _$q_) {
    params = null;
    $scope = $rootScope.$new();
    $q = _$q_;
    accountService = _accountService_;
    taskMonitorService = _taskMonitorService_;
    modalInstance = {};

    this.initialize = function() {
      controller = $controller('ConfirmationModalCtrl', {
        $scope: $scope,
        taskMonitorService: _taskMonitorService_,
        accountService: _accountService_,
        params: params,
        $modalInstance: modalInstance,
      });
    };
  }));

  describe('Verification config', function () {

    it('should require verification if verificationLabel and textToVerify passed in', function () {
      params = {
        verificationLabel: 'please confirm'
      };
      this.initialize();
      expect($scope.verification.requireVerification).toBe(false);

      params.textToVerify = 'yes';
      this.initialize();
      expect($scope.verification.requireVerification).toBe(true);
    });

    it('should require verification if account is passed in and it requires verification', function () {
      params = {
        account: 'prod'
      };
      spyOn(accountService, 'challengeDestructiveActions').and.callFake(function (account) {
        return $q.when(account === 'prod');
      });
      this.initialize();
      $scope.$digest();
      expect($scope.verification.requireVerification).toBe(true);

      params.account = 'test';
      this.initialize();
      $scope.$digest();
      expect($scope.verification.requireVerification).toBe(false);
    });
  });

  describe('task monitor configuration', function () {
    it('should configure the task monitor if config supplied, attaching modalInstance', function () {
      params = {
        taskMonitorConfig: {}
      };
      spyOn(taskMonitorService, 'buildTaskMonitor').and.returnValue('your monitor');
      this.initialize();

      expect($scope.taskMonitor).toBe('your monitor');
      expect(params.taskMonitorConfig.modalInstance).toBe(modalInstance);
    });
  });

  describe('form disabled', function () {
    it('should be disabled when verification is required and not performed', function () {
      params = {
        verificationLabel: 'please confirm',
        textToVerify: 'yes',
      };
      this.initialize();
      expect($scope.verification.requireVerification).toBe(true);
      expect(controller.formDisabled()).toBe(true);

      $scope.verification.userVerification = 'yes';
      expect(controller.formDisabled()).toBe(false);
    });
  });

});
