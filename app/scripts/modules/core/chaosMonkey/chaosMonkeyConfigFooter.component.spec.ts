import footerModule, {ChaosMonkeyConfigFooterController} from './chaosMonkeyConfigFooter.component';

describe('Component: ChaosMonkeyConfigFooter', () => {

  let $componentController: ng.IComponentControllerService,
      applicationWriter: any,
      $ctrl: ChaosMonkeyConfigFooterController,
      $q: ng.IQService,
      $scope: ng.IScope;

  let initializeController = (data: any) => {
    $ctrl = <ChaosMonkeyConfigFooterController> $componentController(
      'chaosMonkeyConfigFooter',
      { $scope: null, applicationWriter: applicationWriter },
      data
    );
  };

  beforeEach(angular.mock.module(footerModule));

  beforeEach(angular.mock.inject((
    _$componentController_: ng.IComponentControllerService,
    _$q_: ng.IQService,
    $rootScope: ng.IRootScopeService,
    _applicationWriter_: any) => {
      $scope = $rootScope.$new();
      $componentController = _$componentController_;
      $q = _$q_;
      applicationWriter = _applicationWriter_;
  }));

  describe('revert', () => {

    it('replaces contents of config with original config', () => {
      let data = {
        viewState: {
          originalConfig: { exceptions: ([] as any), enabled: false }
        },
        config: {
          exceptions: [ {account: 'prod', region: 'us-east-1'} ],
          enabled: true,
          grouping: 'app'
        }
      };

      initializeController(data);
      $ctrl.revert();

      expect($ctrl.config).toEqual(data.config);
      expect($ctrl.config).not.toBe(data.viewState.originalConfig);
      expect(JSON.stringify($ctrl.config)).toBe(JSON.stringify(data.viewState.originalConfig));
    });
  });

  describe('save', () => {
    beforeEach(() => {
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
    it ('sets state to saving, saves, then sets flags appropriately', () => {
      let viewState = this.data.viewState;
      spyOn(applicationWriter, 'updateApplication').and.returnValue($q.when(null));
      initializeController(this.data);
      $ctrl.save();

      expect(viewState.saving).toBe(true);
      expect(viewState.isDirty).toBe(true);

      $scope.$digest();
      expect(viewState.saving).toBe(false);
      expect(viewState.saveError).toBe(false);
      expect(viewState.isDirty).toBe(false);
      expect(viewState.originalConfig).toEqual(this.data.config);
      expect(viewState.originalStringVal).toBe(JSON.stringify(this.data.config));
    });

    it('sets appropriate flags when save fails', () => {
      let viewState = this.data.viewState;
      spyOn(applicationWriter, 'updateApplication').and.returnValue($q.reject(null));
      initializeController(this.data);
      $ctrl.save();

      expect(viewState.saving).toBe(true);
      expect(viewState.isDirty).toBe(true);

      $scope.$digest();
      expect(viewState.saving).toBe(false);
      expect(viewState.saveError).toBe(true);
      expect(viewState.isDirty).toBe(true);
      expect(viewState.originalConfig.enabled).toBe(false);
      expect(viewState.originalStringVal).toBe('original');
    });
  });


});
