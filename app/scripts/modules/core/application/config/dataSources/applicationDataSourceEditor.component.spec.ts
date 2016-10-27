import {Application} from '../../application.model';
import modelBuilderModule, {ApplicationModelBuilder} from '../../applicationModel.builder';
import editorModule, {DataSourceEditorController} from './applicationDataSourceEditor.component';

describe('Component: Application Data Source Editor', () => {

  let applicationWriter: any,
      applicationModelBuilder: ApplicationModelBuilder,
      application: Application,
      $componentController: ng.IComponentControllerService,
      ctrl: DataSourceEditorController,
      $q: ng.IQService,
      $scope: ng.IScope;

  let initialize = () => {
    ctrl = <DataSourceEditorController> $componentController(
      'applicationDataSourceEditor',
      { $scope: null, applicationWriter: applicationWriter },
      { application: application }
    );
    ctrl.$onInit();
  };

  beforeEach(angular.mock.module(
    editorModule,
    modelBuilderModule
  ));

  beforeEach(angular.mock.inject(
    (_applicationWriter_: any, _applicationModelBuilder_: ApplicationModelBuilder,
     _$componentController_: ng.IComponentControllerService, _$q_: ng.IQService, $rootScope: ng.IRootScopeService) => {
      applicationWriter = _applicationWriter_;
      applicationModelBuilder = _applicationModelBuilder_;
      $componentController = _$componentController_;
      $q = _$q_;
      $scope = $rootScope.$new();
  }));

  beforeEach(() => {
    application = applicationModelBuilder.createApplication(
      {
        key: 'optionalSource',
        optional: true,
        visible: true
      },
      {
        key: 'invisibleSource',
        visible: false,
      },
      {
        key: 'requiredSource',
        visible: true,
      },
      {
        key: 'optInSource',
        optional: true,
        visible: true,
        optIn: true,
        disabled: true,
      }
    );
    application.attributes = { accounts: ['test'] };
  });

  describe('model initialization', () => {

    it('uses data source configuration if no dataSources attribute present on application', () => {
      initialize();
      expect(ctrl.model).toEqual({
        optionalSource: true,
        optInSource: false
      });
    });

    it('marks explicitly disabled sources from application data sources', () => {
      application.getDataSource('optionalSource').disabled = true;
      initialize();
      expect(ctrl.model).toEqual({
        optionalSource: false,
        optInSource: false,
      });
    });

    it('marks explicitly enabled opt-in sources from application data sources', () => {
      application.getDataSource('optInSource').disabled = false;
      initialize();
      expect(ctrl.model).toEqual({
        optionalSource: true,
        optInSource: true,
      });
    });
  });

  describe('toggling options', () => {
    it('sets isDirty flag when option changes', () => {
      initialize();
      ctrl.model.optInSource = true;
      ctrl.dataSourceChanged('optInSource');
      expect(ctrl.isDirty).toBe(true);
      ctrl.model.optInSource = false;
      ctrl.dataSourceChanged('optInSource');
      expect(ctrl.isDirty).toBe(false);
    });

    it('adds field to explicitlyEnabled/Disabled when toggled', () => {
      initialize();
      expect(ctrl.explicitlyEnabled).toEqual([]);
      expect(ctrl.explicitlyDisabled).toEqual([]);

      ctrl.model.optInSource = true;
      ctrl.dataSourceChanged('optInSource');
      expect(ctrl.explicitlyEnabled).toEqual(['optInSource']);
      expect(ctrl.explicitlyDisabled).toEqual([]);

      ctrl.model.optInSource = false;
      ctrl.dataSourceChanged('optInSource');
      expect(ctrl.explicitlyEnabled).toEqual([]);
      expect(ctrl.explicitlyDisabled).toEqual(['optInSource']);
    });
  });

  describe('save', () => {
    it('sets state flags, saves, then updates existing data sources and refreshes application', () => {
      spyOn(applicationWriter, 'updateApplication').and.returnValue($q.when());
      spyOn(application, 'refresh').and.returnValue(null);
      initialize();
      expect(ctrl.saving).toBe(false);

      ctrl.model.optInSource = true;
      ctrl.dataSourceChanged('optInSource');
      ctrl.model.optionalSource = false;
      ctrl.dataSourceChanged('optionalSource');
      ctrl.save();

      expect(ctrl.isDirty).toBe(true);
      expect(ctrl.saving).toBe(true);
      expect(applicationWriter.updateApplication.calls.mostRecent().args[0]).toEqual({
        name: 'app',
        accounts: ['test'],
        dataSources: { enabled: ['optInSource'], disabled: ['optionalSource']}
      });

      $scope.$digest();
      expect(ctrl.saving).toBe(false);
      expect(ctrl.isDirty).toBe(false);
      expect(application.attributes.dataSources.enabled).toEqual(['optInSource']);
      expect(application.attributes.dataSources.disabled).toEqual(['optionalSource']);
      expect((application.refresh as any).calls.count()).toEqual(1);
    });

    it('sets error flag when save fails', () => {
      spyOn(applicationWriter, 'updateApplication').and.returnValue($q.reject());
      spyOn(application, 'refresh');
      initialize();
      expect(ctrl.saving).toBe(false);
      expect(ctrl.saveError).toBe(false);

      ctrl.save();
      expect(ctrl.saving).toBe(true);

      $scope.$digest();
      expect(ctrl.saving).toBe(false);
      expect(ctrl.saveError).toBe(true);
    });
  });
});
