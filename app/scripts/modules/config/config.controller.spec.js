'use strict';

describe('Controller: Config', function () {

  var $controller;
  var configController;
  var notificationService;
  var $rootScope;
  var $modal;
  var application;

  beforeEach(window.module(
    'spinnaker.config.controller'
  ));

  beforeEach(
    inject(function (_$rootScope_, _$controller_, _$modal_, _notificationService_) {
      $rootScope = _$rootScope_;
      $controller = _$controller_;
      $modal = _$modal_;
      notificationService = _notificationService_;
    })
  );

  beforeEach(function() {
    spyOn(notificationService, 'getNotificationsForApplication').and.returnValue({then: angular.noop});
  });

  describe('edit application ', function () {
    beforeEach( function() {
        application = {
          serverGroups:[],
          name: 'test-app',
          accounts: 'test'
        };

        configController = $controller('ConfigController', {
          application: application,
          $modal: $modal,
          notificationService: notificationService,
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
