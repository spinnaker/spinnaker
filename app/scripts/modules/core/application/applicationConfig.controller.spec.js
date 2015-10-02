'use strict';

describe('Controller: Config', function () {

  var $controller;
  var configController;
  var notificationService;
  var $rootScope;
  var $modal;
  var application;

  beforeEach(window.module(
    require('./applicationConfig.controller.js')
  ));

  beforeEach(
    window.inject(function (_$rootScope_, _$controller_, _$modal_) {
      $rootScope = _$rootScope_;
      $controller = _$controller_;
      $modal = _$modal_;
    })
  );

  describe('edit application ', function () {
    beforeEach( function() {
        application = {
          serverGroups:[],
          name: 'test-app',
          accounts: 'test'
        };

        configController = $controller('ApplicationConfigController', {
          app: application,
          $modal: $modal,
        });
      }
    );

    it('should copy attributes when edit application is successful', function() {
      var newAttributes = { foo: 'bar' };
      spyOn($modal, 'open').and.returnValue({
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
