import { mock, IQService, IScope } from 'angular';
import React from 'react';
import { shallow } from 'enzyme';

import { CreatePipelineModal, ICreatePipelineModalProps } from './CreatePipelineModal';
import { PipelineTemplateReader } from '../config/templates/PipelineTemplateReader';
import { Application } from '../../application/application.model';
import { ApplicationModelBuilder } from '../../application/applicationModel.builder';
import { IPipeline } from '../../domain';
import { SETTINGS } from '../../config/settings';
import { PipelineConfigService } from '../config/services/PipelineConfigService';

// Disable CreatePipelineModal tests until enzyme supports React 16 https://github.com/airbnb/enzyme/issues/1553
// CreatePipelineModal uses Overridable() which uses React.forwardRef
xdescribe('CreatePipelineModal', () => {
  let $q: IQService;
  let $scope: IScope;
  let application: Application;
  let initializeComponent: (configs?: Array<Partial<IPipeline>>) => void;
  let component: CreatePipelineModal;

  beforeEach(
    mock.inject((_$q_: IQService, $rootScope: IScope) => {
      $q = _$q_;
      $scope = $rootScope.$new();
      initializeComponent = (configs = []) => {
        application = ApplicationModelBuilder.createApplicationForTests(
          'app',
          {
            key: 'pipelineConfigs',
            lazy: true,
            loader: () => $q.resolve(application.pipelineConfigs.data),
            onLoad: (_app, data) => $q.resolve(data),
            defaultData: [],
          },
          {
            key: 'strategyConfigs',
            lazy: true,
            loader: () => $q.resolve(application.strategyConfigs.data),
            onLoad: (_app, data) => $q.resolve(data),
            defaultData: [],
          },
        );
        application.pipelineConfigs.data = configs;

        const props: ICreatePipelineModalProps = {
          application,
          show: true,
          showCallback: (): void => null,
          pipelineSavedCallback: (): void => null,
        };

        const RealCreatePipelineModal = (CreatePipelineModal as any).OriginalComponent as React.ComponentType<any>;
        component = shallow(<RealCreatePipelineModal {...props} />).instance() as CreatePipelineModal;
      };
    }),
  );

  describe('config instantiation', () => {
    it('provides a default value when no configs exist', () => {
      initializeComponent();
      const config = component.state.configs[0];
      expect(component.state.configs.length).toBe(1);
      expect(config.name).toBe('None');
      expect(config.application).toBe('app');
      expect(config.triggers).toEqual([]);
      expect(config.stages).toEqual([]);
    });

    it('includes the default value when configs exist', () => {
      initializeComponent([{ name: 'some pipeline' }]);
      expect(component.state.configs.length).toBe(2);
      expect(component.state.configs[0].name).toBe('None');
      expect(component.state.configs[1].name).toBe('some pipeline');
    });

    it('initializes command with the default config', () => {
      initializeComponent([{ name: 'some pipeline' }]);
      expect(component.state.configs.length).toBe(2);
      expect(component.state.configs[0].name).toBe('None');
      expect(component.state.configs[1].name).toBe('some pipeline');
      expect(component.state.command.config.name).toBe('None');
    });

    it(`includes all config names in the component's state to be used to determine if a name is unique`, () => {
      initializeComponent([{ name: 'a' }, { name: 'b' }]);
      expect(component.state.configs.length).toBe(3);
      expect(component.state.existingNames).toEqual(['None', 'a', 'b']);
    });
  });

  describe('template initialization', () => {
    beforeEach(() => (SETTINGS.feature.pipelineTemplates = true));
    afterEach(SETTINGS.resetToOriginal);

    it('loads pipeline templates', () => {
      spyOn(PipelineTemplateReader, 'getPipelineTemplatesByScopes').and.callFake(() => {
        const templates = [
          {
            id: 'templateA',
            scopes: ['global'],
          },
          {
            id: 'templateB',
            scopes: ['myApp'],
          },
        ] as any;
        return $q.resolve(templates);
      });

      component.loadPipelineTemplates();
      $scope.$digest();

      expect(component.state.templates.map((t) => t.id)).toEqual(['templateA', 'templateB']);
    });

    it('sets error flag, message when load is rejected', () => {
      spyOn(PipelineTemplateReader, 'getPipelineTemplatesByScopes').and.callFake(() => {
        return $q.reject(null);
      });

      component.loadPipelineTemplates();
      $scope.$digest();

      expect(component.state.loadError).toEqual(true);
      expect(component.state.loadErrorMessage).toEqual('No message provided');
    });
  });

  describe('pipeline name validation', () => {
    const setPipelineName = (_component: CreatePipelineModal, name: string): void => {
      _component.setState({ command: { ..._component.state.command, name } });
    };

    it('verifies that the pipeline name does not contain invalid characters', () => {
      initializeComponent();
      setPipelineName(component, '\\');
      expect(component.validateNameCharacters()).toEqual(false);
      setPipelineName(component, '^');
      expect(component.validateNameCharacters()).toEqual(false);
      setPipelineName(component, '?');
      expect(component.validateNameCharacters()).toEqual(false);
      setPipelineName(component, '%');
      expect(component.validateNameCharacters()).toEqual(false);
      setPipelineName(component, '#');
      expect(component.validateNameCharacters()).toEqual(false);
      setPipelineName(component, 'validName');
      expect(component.validateNameCharacters()).toEqual(true);
    });

    it('verifies that the pipeline name is unique', () => {
      initializeComponent([{ name: 'a' }, { name: 'b' }]);
      setPipelineName(component, 'a');
      expect(component.validateNameIsUnique()).toEqual(false);
      setPipelineName(component, 'b');
      expect(component.validateNameIsUnique()).toEqual(false);
      setPipelineName(component, 'c');
      expect(component.validateNameIsUnique()).toEqual(true);
    });
  });

  describe('pipeline submission', () => {
    it('saves pipeline, adds it to application', () => {
      initializeComponent();
      let submitted: IPipeline = null;

      spyOn(application.pipelineConfigs, 'refresh').and.callFake(() => {
        application.pipelineConfigs.data = [{ name: 'new pipeline', id: '1234-5678' }];
        return $q.when(null);
      });
      spyOn(PipelineConfigService, 'savePipeline').and.callFake((pipeline: IPipeline) => {
        submitted = pipeline;
        return $q.when(null);
      });

      component.setState({ command: { ...component.state.command, name: 'new pipeline' } });

      component.submit();
      $scope.$digest();

      expect(submitted.name).toBe('new pipeline');
      expect(submitted.application).toBe('app');
      expect(submitted.stages).toEqual([]);
      expect(submitted.triggers).toEqual([]);
    });

    it('uses copy of plain version of pipeline', () => {
      let submitted: IPipeline = null;
      const toCopy = {
        application: 'the_app',
        name: 'old_name',
        triggers: [{ name: 'the_trigger', enabled: true, type: 'git' }],
      };
      initializeComponent([toCopy]);

      spyOn(application.pipelineConfigs, 'refresh').and.callFake(() => {
        application.pipelineConfigs.data = [{ name: 'new pipeline', id: '1234-5678' }];
        return $q.when(null);
      });
      spyOn(PipelineConfigService, 'savePipeline').and.callFake((pipeline: IPipeline) => {
        submitted = pipeline;
        return $q.when(null);
      });

      component.state.command.name = 'new pipeline';
      component.state.command.config = toCopy;

      component.submit();
      $scope.$digest();

      expect(submitted.name).toBe('new pipeline');
      expect(submitted.application).toBe('the_app');
      expect(submitted.triggers.length).toBe(1);
    });

    it('should insert new pipeline as last one in application and set its index', () => {
      let submitted: IPipeline = null;
      initializeComponent([{ name: 'x' }]);

      spyOn(application.pipelineConfigs, 'refresh').and.callFake(() => {
        application.pipelineConfigs.data = [{ name: 'new pipeline', id: '1234-5678' }];
        return $q.when(null);
      });
      spyOn(PipelineConfigService, 'savePipeline').and.callFake((pipeline: IPipeline) => {
        submitted = pipeline;
        return $q.when(null);
      });

      component.state.command.name = 'new pipeline';

      component.submit();
      $scope.$digest();

      expect(submitted.index).toBe(1);
    });

    it('sets error flag, message when save is rejected', () => {
      initializeComponent();
      spyOn(PipelineConfigService, 'savePipeline').and.callFake(() => {
        return $q.reject({ data: { message: 'something went wrong' } });
      });

      component.submit();
      $scope.$digest();

      expect(component.state.saveError).toBe(true);
      expect(component.state.saveErrorMessage).toBe('something went wrong');
    });

    it('provides default error message when none provided on failed save', () => {
      initializeComponent();
      spyOn(PipelineConfigService, 'savePipeline').and.callFake(() => {
        return $q.reject({});
      });

      component.submit();
      $scope.$digest();

      expect(component.state.saveError).toBe(true);
      expect(component.state.saveErrorMessage).toBe('No message provided');
    });
  });
});
