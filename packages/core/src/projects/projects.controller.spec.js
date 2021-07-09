'use strict';

import { ProjectReader } from './service/ProjectReader';

describe('Controller: Projects', function () {
  beforeEach(window.module(require('./projects.controller').name, require('angular-ui-bootstrap')));

  describe('filtering', function () {
    var deck = { name: 'deck', email: 'a@netflix.com', createTs: new Date(2) },
      oort = { name: 'oort', email: 'b@netflix.com', createTs: new Date(3) },
      mort = { name: 'mort', email: 'c@netflix.com', createTs: new Date(1) },
      projectList = [deck, oort, mort];

    // Initialize the controller and a mock scope
    beforeEach(
      window.inject(function ($controller, $rootScope, $window, $q, $uibModal, $log, $filter, $state, $timeout) {
        this.$scope = $rootScope.$new();
        this.$q = $q;

        spyOn(ProjectReader, 'listProjects').and.callFake(function () {
          return $q.when(projectList);
        });

        this.ctrl = $controller('ProjectsCtrl', {
          $scope: this.$scope,
          $uibModal: $uibModal,
          $log: $log,
          $filter: $filter,
          $state: $state,
          $timeout: $timeout,
        });

        this.$scope.viewState.projectFilter = '';
        this.$scope.viewState.sortModel.key = 'name';
      }),
    );

    it('sets projectsLoaded flag when projects retrieved and added to scope', function () {
      var $scope = this.$scope;

      expect($scope.projectsLoaded).toBe(false);
      expect($scope.projects).toBeUndefined();

      $scope.$digest();

      expect($scope.projectsLoaded).toBe(true);
      expect($scope.projects).toBe(projectList);
      expect($scope.filteredProjects).toEqual([deck, mort, oort]);
    });

    it('filters projects by name or email', function () {
      var $scope = this.$scope,
        ctrl = this.ctrl;

      $scope.viewState.projectFilter = 'a@netflix.com';
      $scope.$digest();
      expect($scope.projects).toBe(projectList);
      expect($scope.filteredProjects).toEqual([deck]);

      $scope.viewState.projectFilter = 'ort';
      ctrl.filterProjects();
      expect($scope.filteredProjects).toEqual([mort, oort]);
    });

    it('sorts and filters projects', function () {
      var $scope = this.$scope,
        ctrl = this.ctrl;

      $scope.viewState.sortModel.key = '-name';
      $scope.$digest();
      expect($scope.filteredProjects).toEqual([oort, mort, deck]);

      $scope.viewState.sortModel.key = '-createTs';
      ctrl.filterProjects();
      expect($scope.filteredProjects).toEqual([oort, deck, mort]);

      $scope.viewState.sortModel.key = 'createTs';
      ctrl.filterProjects();
      expect($scope.filteredProjects).toEqual([mort, deck, oort]);

      $scope.viewState.projectFilter = 'ort';
      ctrl.filterProjects();
      expect($scope.filteredProjects).toEqual([mort, oort]);
    });
  });
});
