'use strict';

describe('Controller: Config', function () {
  var $controller;
  var configController;
  var $uibModal;
  var application;

  beforeEach(window.module(require('./applicationAttributes.directive').name, require('angular-ui-bootstrap')));

  beforeEach(
    window.inject(function (_$controller_, _$uibModal_) {
      $controller = _$controller_;
      $uibModal = _$uibModal_;
    }),
  );

  describe('edit application ', function () {
    beforeEach(function () {
      application = {
        serverGroups: [],
        name: 'test-app',
        accounts: 'test',
      };

      configController = $controller(
        'ApplicationAttributesCtrl',
        {
          $uibModal: $uibModal,
        },
        { application: application },
      );
    });

    it('should copy attributes when edit application is successful', function () {
      var newAttributes = { foo: 'bar' };
      const modalStub = {
        result: {
          then: function (method) {
            method(newAttributes);
            return modalStub.result;
          },
          catch: () => {},
        },
      };
      spyOn($uibModal, 'open').and.returnValue(modalStub);

      configController.editApplication();
      expect(application.attributes).toBe(newAttributes);
    });
  });
});
