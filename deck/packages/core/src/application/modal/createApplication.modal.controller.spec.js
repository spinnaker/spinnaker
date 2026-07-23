'use strict';

const { AccountService } = require('../../account/AccountService');
const { ApplicationReader } = require('../service/ApplicationReader');

describe('Controller: CreateApplicationModalCtrl', function () {
  //NOTE: This is only testing the controllers dependencies. Please add more tests.

  var controller;
  var scope;

  beforeEach(window.module(require('./createApplication.modal.controller').name));

  beforeEach(function () {
    spyOn(AccountService, 'listProviders').and.returnValue(Promise.resolve([]));
    spyOn(ApplicationReader, 'listApplications').and.returnValue(Promise.resolve([]));
  });

  beforeEach(
    window.inject(function ($rootScope, $controller) {
      scope = $rootScope.$new();
      controller = $controller('CreateApplicationModalCtrl', {
        $scope: scope,
        $uibModalInstance: {},
        name: 'deck',
      });
    }),
  );

  it('should instantiate the controller', function () {
    expect(controller).toBeDefined();
  });
});
