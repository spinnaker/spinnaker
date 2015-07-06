'use strict';

describe('Controller: Applications', function () {

  beforeEach(loadDeckWithoutCacheInitializer);

  beforeEach(
    module(
      'spinnaker.application.controller',
      'spinnaker.applications.read.service'
    )
  );

  describe('filtering', function() {

    var deck = { name: 'deck', email: 'a@netflix.com', createTs: new Date(2) },
        oort = { name: 'oort', email: 'b@netflix.com', createTs: new Date(3) },
        mort = { name: 'mort', email: 'c@netflix.com', createTs: new Date(1) },
        applicationList = [ deck, oort, mort ];

    // Initialize the controller and a mock scope
    beforeEach(inject(function ($controller, $rootScope, $window, $q, $modal, $log, $filter, accountService,
                                $state, $timeout, settings, applicationReader) {

      this.$scope = $rootScope.$new();
      this.settings = settings;
      this.$q = $q;
      this.accountService = accountService;
      this.applicationReader = applicationReader;

      spyOn(this.applicationReader, 'listApplications').and.callFake(function () {
        return $q.when(applicationList);
      });

      spyOn(this.accountService, 'listAccounts').and.callFake(function() {
        return $q.when([]);
      });

      this.ctrl = $controller('ApplicationsCtrl', {
        $scope: this.$scope,
        $modal: $modal,
        $log: $log,
        $filter: $filter,
        accountService: accountService,
        $state: $state,
        $timeout: $timeout
      });

    }));

    it('sets applicationsLoaded flag when applications retrieved and added to scope', function () {
      var $scope = this.$scope;

      expect($scope.applicationsLoaded).toBe(false);
      expect($scope.applications).toBeUndefined();

      $scope.$digest();

      expect($scope.applicationsLoaded).toBe(true);
      expect($scope.applications).toBe(applicationList);
      expect($scope.filteredApplications).toEqual([deck, mort, oort]);

    });

    it('filters applications by name or email', function () {
      var $scope = this.$scope,
          ctrl = this.ctrl;

      $scope.applicationFilter = 'a@netflix.com';
      $scope.$digest();
      expect($scope.applications).toBe(applicationList);
      expect($scope.filteredApplications).toEqual([deck]);

      $scope.applicationFilter = 'ort';
      ctrl.filterApplications();
      expect($scope.filteredApplications).toEqual([mort, oort]);
    });

    it('sorts and filters applications', function() {
      var $scope = this.$scope,
          ctrl = this.ctrl;

      $scope.sortModel.key = '-name';
      $scope.$digest();
      expect($scope.filteredApplications).toEqual([oort, mort, deck]);

      $scope.sortModel.key = '-createTs';
      ctrl.filterApplications();
      expect($scope.filteredApplications).toEqual([oort, deck, mort]);

      $scope.sortModel.key = 'createTs';
      ctrl.filterApplications();
      expect($scope.filteredApplications).toEqual([mort, deck, oort]);

      $scope.applicationFilter = 'ort';
      ctrl.filterApplications();
      expect($scope.filteredApplications).toEqual([mort, oort]);
    });


  });
}).name;
