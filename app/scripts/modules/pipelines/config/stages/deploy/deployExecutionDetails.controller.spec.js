'use strict';

describe('DeployExecutionDetailsCtrl', function() {

  beforeEach(module('deckApp.pipelines.stage.deploy.details.controller'));

  beforeEach(inject(function ($controller, $rootScope, _, $timeout) {
    this.$controller = $controller;
    this._ = _;
    this.$timeout = $timeout;
    this.$scope = $rootScope.$new();

  }));

  describe('deployment results', function() {
    beforeEach(function() {
      var $scope = this.$scope,
        _ = this._;
      $scope.stage = {};
      this.initializeController = function () {
        this.controller = this.$controller('DeployExecutionDetailsCtrl', {
          $scope: $scope,
          _: _,
          $stateParams: { details: 'deploymentConfig' },
          executionDetailsSectionService: jasmine.createSpyObj('executionDetailsSectionService', ['synchronizeSection']),
        });
        this.$timeout.flush();
      };

    });
    it('sets empty list when no context or empty context', function() {

      var stage = this.$scope.stage;

      this.initializeController();
      expect(this.$scope.deployed.length).toBe(0);

      stage.context = {};
      this.initializeController();
      expect(this.$scope.deployed.length).toBe(0);

    });

    it('sets empty list when no kato.tasks or empty kato.tasks', function() {

      var stage = this.$scope.stage;

      stage.context = {};
      this.initializeController();
      expect(this.$scope.deployed.length).toBe(0);

      stage.context = {
        'kato.tasks': [],
      };

      this.initializeController();
      expect(this.$scope.deployed.length).toBe(0);

      stage.context['kato.tasks'].push({});
      this.initializeController();
      expect(this.$scope.deployed.length).toBe(0);

      stage.context['kato.tasks'][0].resultObjects = [];
      this.initializeController();
      expect(this.$scope.deployed.length).toBe(0);

      stage.context['kato.tasks'][0].resultObjects.push({});
      this.initializeController();
      expect(this.$scope.deployed.length).toBe(0);

      stage.context['kato.tasks'][0].resultObjects[0].asgNameByRegion = {
        'us-west-1': 'deployedAsg',
      };
      this.initializeController();
      expect(this.$scope.deployed.length).toBe(1);
      expect(this.$scope.deployed[0].region).toBe('us-west-1');
      expect(this.$scope.deployed[0].name).toBe('deployedAsg');

    });

    it('sets empty list when no resultObjects or empty resultObjects', function() {

      var stage = this.$scope.stage;

      stage.context = { 'kato.tasks': [ {} ] };

      this.initializeController();
      expect(this.$scope.deployed.length).toBe(0);

      stage.context['kato.tasks'][0].resultObjects = [];
      this.initializeController();
      expect(this.$scope.deployed.length).toBe(0);

    });

    it('sets empty list when no asgNameByRegion', function() {

      var stage = this.$scope.stage;

      stage.context = { 'kato.tasks': [ {
        resultObjects: [ { someField: true } ]
      } ] };

      this.initializeController();
      expect(this.$scope.deployed.length).toBe(0);

      stage.context['kato.tasks'][0].resultObjects[0].asgNameByRegion = {
        'us-west-1': 'deployedAsg',
      };
      this.initializeController();
      expect(this.$scope.deployed.length).toBe(1);
      expect(this.$scope.deployed[0].region).toBe('us-west-1');
      expect(this.$scope.deployed[0].name).toBe('deployedAsg');

    });

    it('sets deployed when asgNameByRegion supplies values', function() {
      var stage = this.$scope.stage;

      stage.context = {
        'kato.tasks': [
          {
            resultObjects: [
              {
                asgNameByRegion: { 'us-west-1': 'deployedWest', 'us-east-1': 'deployedEast' }
              }
            ]
          }
        ]
      };

      this.initializeController();
      expect(this.$scope.deployed.length).toBe(2);

    });

  });

});
