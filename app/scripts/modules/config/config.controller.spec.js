'use strict';

xdescribe('Controller: Config', function () {

  var $controller;
  var configController;
  var applicationWriter;
  var $state;
  var $rootScope;
  var $q;

  beforeEach(window.module(
    'spinnaker.config.controller'
  ));

  beforeEach(
    window.inject(function (_$rootScope_, _$controller_, _$q_, _applicationWriter_, _$state_) {
      $rootScope = _$rootScope_;
      $controller = _$controller_;
      applicationWriter = _applicationWriter_;
      $state = _$state_;
      $q = _$q_;
    })
  );


  describe('delete an application ', function () {
    beforeEach( function() {
        configController = $controller('ConfigController', {
          applicationWriter: applicationWriter,
          application: {
            serverGroups:[],
            name: 'test-app',
            accounts: 'test'
          }
        });
      }
    );

    it('should delete the app and route to the applications list page', function () {
      var deferred = $q.defer();

      spyOn(applicationWriter, 'deleteApplication').and.returnValue(deferred.promise);
      spyOn($state, 'go');

      configController.deleteApplication();
      deferred.resolve('all good');
      $rootScope.$apply();

      expect(applicationWriter.deleteApplication).toHaveBeenCalled();
      expect($state.go).toHaveBeenCalledWith('home.applications');
    });

    it('should show an error if the delete fails', function () {
      var deferred = $q.defer();

      spyOn(applicationWriter, 'deleteApplication').and.returnValue(deferred.promise);
      configController.deleteApplication();

      deferred.reject(new Error('failed'));
      $rootScope.$apply();

      expect(applicationWriter.deleteApplication).toHaveBeenCalled();
      expect(configController.error).toBe('failed');
    });
  });



});
