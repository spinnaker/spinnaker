'use strict';

describe('Controller: Config', function () {

  var $controller;
  var configController;
  var $rootScope;
  var $uibModal;
  var application;

  beforeEach(window.module(
    require('./applicationAttributes.directive.js')
  ));

  beforeEach(
    window.inject(function (_$rootScope_, _$controller_, _$uibModal_) {
      $rootScope = _$rootScope_;
      $controller = _$controller_;
      $uibModal = _$uibModal_;
    })
  );

  describe('edit application ', function () {
    beforeEach( function() {
        application = {
          serverGroups:[],
          name: 'test-app',
          accounts: 'test'
        };

        configController = $controller('ApplicationAttributesCtrl', {
          $uibModal: $uibModal,
        }, {application: application});
      }
    );

    it('should copy attributes when edit application is successful', function() {
      var newAttributes = { foo: 'bar' };
      spyOn($uibModal, 'open').and.returnValue({
        result: {
          then: function(method) {
            method(newAttributes);
          }
        }
      });

      configController.editApplication();
      expect(application.attributes).toBe(newAttributes);
    });
  });



});
