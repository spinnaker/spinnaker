'use strict';

require('angular');

describe('Controller: ChaosMonkeyExceptions', function () {

  var accountService,
      vm,
      scope,
      $q;

  beforeEach(window.module(require('./chaosMonkeyExceptions.directive.js')));

  beforeEach(window.inject(function ($controller, $rootScope, _accountService_, _$q_) {
    scope = $rootScope.$new();
    vm = $controller;
    accountService = _accountService_;
    $q = _$q_;

    this.initializeController = function() {
      vm = $controller('ChaosMonkeyExceptionsCtrl', {
        accountService: accountService,
        $q: $q
      });
    };
  }));

  describe('data initialization', function () {

    it('gets all accounts, then adds wildcard and regions per account to vm', function () {
      let accounts = [ {name: 'prod'}, {name: 'test'} ];
      let details = {
        prod: { name: 'prod', regions: [ {name: 'us-east-1'}, {name: 'us-west-1'}] },
        test: { name: 'test', regions: [ {name: 'us-west-2'}, {name: 'eu-west-1'}] }
      };

      spyOn(accountService, 'listAccounts').and.returnValue($q.when(accounts));
      spyOn(accountService, 'getAccountDetails').and.callFake((accountName) => {
        return $q.when(details[accountName]);
      });

      this.initializeController();
      scope.$digest();

      expect(vm.accounts).toEqual([details.prod, details.test]);
      expect(vm.regionsByAccount).toEqual({
        prod: [ '*', 'us-east-1', 'us-west-1'],
        test: [ '*', 'us-west-2', 'eu-west-1']
      });
    });
  });


});
