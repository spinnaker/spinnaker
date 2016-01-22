'use strict';

describe('Controller: UserVerification', function () {
  var controller, accountService, $scope, $q;

  beforeEach(
    window.module(
      require('./userVerification.directive')
    )
  );

  beforeEach(window.inject(function($controller, $rootScope, _accountService_, _$q_) {
    $scope = $rootScope.$new();
    $q = _$q_;
    accountService = _accountService_;

    this.initialize = function(verification, account) {
      controller = $controller('UserVerificationCtrl',
        {
          $scope: $scope,
          accountService: accountService,
        },
        {
          verification: verification,
          account: account
        });
      $scope.$digest();
    };
  }));

  describe('initialization', function () {

    it('should set required to true, verified to false if toVerify is present', function () {
      spyOn(accountService, 'challengeDestructiveActions').and.returnValue($q.when(false));
      let verification = {
        toVerify: 'a'
      };
      this.initialize(verification);
      expect(controller.required).toBe(true);
      expect(verification.verified).toBe(false);
    });

    it('should set required to true, verified to false if account requires challenge to destructive actions', function () {
      spyOn(accountService, 'challengeDestructiveActions').and.returnValue($q.when(true));
      let verification = {};
      this.initialize(verification, 'prod');
      expect(controller.required).toBe(true);
      expect(verification.verified).toBe(false);
    });

    it('should set required to false, verified to true if account does not require challenge to destructive actions', function () {
      spyOn(accountService, 'challengeDestructiveActions').and.returnValue($q.when(false));
      let verification = {};
      this.initialize(verification, 'test');
      expect(controller.required).toBe(false);
      expect(verification.verified).toBe(true);
    });

    it('should re-initialize when account changes', function () {
      spyOn(accountService, 'challengeDestructiveActions').and.callFake(function(account) {
        return $q.when(account === 'prod');
      });
      let verification = {};
      this.initialize(verification, 'test');
      expect(controller.required).toBe(false);
      expect(verification.verified).toBe(true);

      controller.account = 'prod';
      $scope.$digest();

      expect(controller.required).toBe(true);
      expect(verification.verified).toBe(false);
    });

    it('should prefer account when both toVerify and account are present', function () {
      spyOn(accountService, 'challengeDestructiveActions').and.returnValue($q.when(false));
      let verification = {
        toVerify: 'a'
      };
      this.initialize(verification, 'someAccount');
      expect(controller.required).toBe(false);
      expect(verification.verified).toBe(true);
    });
  });

  describe('verify', function () {

    it('should set verified to false when entry does not match', function () {
      let verification = { toVerify: 'a' };
      this.initialize(verification);
      controller.userVerification = 'b';
      controller.verify();
      expect(verification.verified).toBe(false);
    });

    it('should set verified to false when nothing entered', function () {
      let verification = { toVerify: 'a' };
      this.initialize(verification);
      controller.verify();
      expect(verification.verified).toBe(false);
    });

    it('should set verified to true when entry matches, ignoring case', function () {
      let verification = { toVerify: 'a' };
      this.initialize(verification);

      controller.userVerification = 'a';
      controller.verify();
      expect(verification.verified).toBe(true);

      controller.userVerification = 'b';
      controller.verify();
      expect(verification.verified).toBe(false);

      controller.userVerification = 'A';
      controller.verify();
      expect(verification.verified).toBe(true);
    });
  });

});
