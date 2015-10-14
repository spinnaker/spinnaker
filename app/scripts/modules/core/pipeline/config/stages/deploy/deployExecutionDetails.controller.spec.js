'use strict';

describe('DeployExecutionDetailsCtrl', function() {

  beforeEach(
    window.module(
      require('./deployExecutionDetails.controller')
    )
  );

  beforeEach(window.inject(function ($controller, $rootScope, _, $timeout) {
    this.$controller = $controller;
    this._ = _;
    this.$timeout = $timeout;
    this.$scope = $rootScope.$new();
    this.urlBuilderService = { buildFromMetadata: function() { return '#'; }};

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
          urlBuilderService: this.urlBuilderService,
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

      stage.context['kato.tasks'][0].resultObjects[0].serverGroupNameByRegion = {
        'us-west-1': 'deployedAsg',
      };
      this.initializeController();
      expect(this.$scope.deployed.length).toBe(1);
      expect(this.$scope.deployed[0].serverGroup).toBe('deployedAsg');

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

    it('sets empty list when no serverGroupNameByRegion', function() {

      var stage = this.$scope.stage;

      stage.context = { 'kato.tasks': [ {
        resultObjects: [ { someField: true } ]
      } ] };

      this.initializeController();
      expect(this.$scope.deployed.length).toBe(0);

      stage.context['kato.tasks'][0].resultObjects[0].serverGroupNameByRegion = {
        'us-west-1': 'deployedAsg',
      };
      this.initializeController();
      expect(this.$scope.deployed.length).toBe(1);
      expect(this.$scope.deployed[0].serverGroup).toBe('deployedAsg');

    });

    it('sets deployed when serverGroupNameByRegion supplies values', function() {
      var stage = this.$scope.stage;

      stage.context = {
        'kato.tasks': [
          {
            resultObjects: [
              {
                serverGroupNameByRegion: { 'us-west-1': 'deployedWest', 'us-east-1': 'deployedEast' }
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
