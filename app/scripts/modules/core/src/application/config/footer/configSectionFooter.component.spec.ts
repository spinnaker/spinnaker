import { mock } from 'angular';

import { ApplicationWriter } from '../../service/ApplicationWriter';
import { CONFIG_SECTION_FOOTER, ConfigSectionFooterController } from './configSectionFooter.component';

describe('Component: ConfigSectionFooter', () => {
  let $componentController: ng.IComponentControllerService,
    $ctrl: ConfigSectionFooterController,
    $q: ng.IQService,
    $scope: ng.IScope;

  const initializeController = (data: any) => {
    $ctrl = $componentController('configSectionFooter', { $scope: null }, data) as ConfigSectionFooterController;
  };

  beforeEach(mock.module(CONFIG_SECTION_FOOTER));

  beforeEach(
    mock.inject(
      (
        _$componentController_: ng.IComponentControllerService,
        _$q_: ng.IQService,
        $rootScope: ng.IRootScopeService,
      ) => {
        $scope = $rootScope.$new();
        $componentController = _$componentController_;
        $q = _$q_;
      },
    ),
  );

  describe('revert', () => {
    it('replaces contents of config with original config', () => {
      const data = {
        viewState: {
          originalConfig: { exceptions: [] as any, enabled: false },
        },
        config: {
          exceptions: [{ account: 'prod', region: 'us-east-1' }],
          enabled: true,
          grouping: 'app',
        },
      };

      initializeController(data);
      $ctrl.revert();

      expect($ctrl.config).toEqual(data.config);
      expect($ctrl.config).not.toBe(data.viewState.originalConfig);
      expect(JSON.stringify($ctrl.config)).toBe(JSON.stringify(data.viewState.originalConfig));
    });
  });

  describe('save', () => {
    let data: any;
    beforeEach(() => {
      data = {
        application: { name: 'deck', attributes: { accounts: ['prod'] } },
        viewState: {
          originalConfig: { exceptions: [], enabled: false },
          originalStringVal: 'original',
          saving: false,
          saveError: false,
          isDirty: true,
        },
        config: {
          exceptions: [{ account: 'prod', region: 'us-east-1' }],
          enabled: true,
          grouping: 'app',
        },
      };
    });

    it('sets state to saving, saves, then sets flags appropriately', () => {
      const viewState = data.viewState;
      spyOn(ApplicationWriter, 'updateApplication').and.returnValue($q.when(null));
      initializeController(data);
      $ctrl.save();

      expect(viewState.saving).toBe(true);
      expect(viewState.isDirty).toBe(true);

      $scope.$digest();
      expect(viewState.saving).toBe(false);
      expect(viewState.saveError).toBe(false);
      expect(viewState.isDirty).toBe(false);
      expect(viewState.originalConfig).toEqual(data.config);
      expect(viewState.originalStringVal).toBe(JSON.stringify(data.config));
    });

    it('sets appropriate flags when save fails', () => {
      const viewState = data.viewState;
      spyOn(ApplicationWriter, 'updateApplication').and.returnValue($q.reject(null));
      initializeController(data);
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
