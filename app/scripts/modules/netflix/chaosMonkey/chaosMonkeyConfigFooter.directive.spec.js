'use strict';

require('angular');

describe('Controller: ChaosMonkeyConfigFooter', function () {

  var applicationWriter,
      vm,
      scope,
      $q;

  beforeEach(window.module(require('./chaosMonkeyConfigFooter.directive.js')));

  beforeEach(window.inject(function ($controller, $rootScope, _applicationWriter_, _$q_) {
    scope = $rootScope.$new();
    vm = $controller;
    applicationWriter = _applicationWriter_;
    $q = _$q_;

    this.initializeController = function(data) {
      vm = $controller('ChaosMonkeyConfigFooterCtrl', {
        $scope: scope,
        applicationWriter: applicationWriter,
      }, data);
    };
  }));

  describe('revert', function () {

    it('replaces contents of config with original config', function () {
      var data = {
        viewState: {
          originalConfig: { exceptions: [], enabled: false }
        },
        config: {
          exceptions: [ {account: 'prod', region: 'us-east-1'} ],
          enabled: true,
          grouping: 'app'
        }
      };

      this.initializeController(data);
      vm.revert();

      expect(vm.config).toBe(data.config);
      expect(vm.config).not.toBe(data.viewState.originalConfig);
      expect(JSON.stringify(vm.config)).toBe(JSON.stringify(data.viewState.originalConfig));
    });
  });

  describe('save', function () {
    beforeEach(function () {
      this.data = {
        application: { name: 'deck', attributes: { accounts: ['prod']}},
        viewState: {
          originalConfig: { exceptions: [], enabled: false },
          originalStringVal: 'original',
          saving: false,
          saveError: false,
          isDirty: true,
        },
        config: {
          exceptions: [ {account: 'prod', region: 'us-east-1'} ],
          enabled: true,
          grouping: 'app'
        }
      };
    });
    it ('sets state to saving, saves, then sets flags appropriately', function () {
      var viewState = this.data.viewState;
      spyOn(applicationWriter, 'updateApplication').and.returnValue($q.when(null));
      this.initializeController(this.data);
      vm.save();

      expect(viewState.saving).toBe(true);
      expect(viewState.isDirty).toBe(true);

      scope.$digest();
      expect(viewState.saving).toBe(false);
      expect(viewState.saveError).toBe(false);
      expect(viewState.isDirty).toBe(false);
      expect(viewState.originalConfig).toBe(this.data.config);
      expect(viewState.originalStringVal).toBe(JSON.stringify(this.data.config));
    });

    it('sets appropriate flags when save fails', function () {
      var viewState = this.data.viewState;
      spyOn(applicationWriter, 'updateApplication').and.returnValue($q.reject(null));
      this.initializeController(this.data);
      vm.save();

      expect(viewState.saving).toBe(true);
      expect(viewState.isDirty).toBe(true);

      scope.$digest();
      expect(viewState.saving).toBe(false);
      expect(viewState.saveError).toBe(true);
      expect(viewState.isDirty).toBe(true);
      expect(viewState.originalConfig.enabled).toBe(false);
      expect(viewState.originalStringVal).toBe('original');
    });
  });


});
