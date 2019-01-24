'use strict';

describe('Controller: ConfirmationModal', function() {
  var controller, params, $scope, modalInstance;

  beforeEach(window.module(require('./confirmationModal.controller').name));

  beforeEach(
    window.inject(function($controller, $rootScope) {
      params = null;
      $scope = $rootScope.$new();
      modalInstance = {};

      this.initialize = function() {
        controller = $controller('ConfirmationModalCtrl', {
          $scope: $scope,
          params: params,
          $uibModalInstance: modalInstance,
        });
      };
    }),
  );

  describe('Verification config', function() {
    it('should require verification if verificationLabel and textToVerify passed in', function() {
      params = {
        verificationLabel: 'please confirm',
      };
      this.initialize();
      expect($scope.verification.required).toBe(false);

      params.textToVerify = 'yes';
      this.initialize();
      expect($scope.verification.required).toBe(true);
    });

    it('should require verification if account is passed in', function() {
      params = {
        account: 'prod',
      };
      this.initialize();
      $scope.$digest();
      expect($scope.verification.required).toBe(true);
    });
  });

  describe('form disabled', function() {
    it('should be disabled when verification is required and not performed', function() {
      params = {
        verificationLabel: 'please confirm',
        textToVerify: 'yes',
      };
      this.initialize();
      expect($scope.verification.required).toBe(true);
      expect(controller.formDisabled()).toBe(true);

      $scope.verification.verified = true;
      expect(controller.formDisabled()).toBe(false);
    });
  });
});
