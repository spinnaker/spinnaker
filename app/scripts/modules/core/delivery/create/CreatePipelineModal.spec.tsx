import {mock, IQService, IScope} from 'angular';
import * as React from 'react';
import {shallow} from 'enzyme';

import {CreatePipelineModal, ICreatePipelineModalProps} from './CreatePipelineModal';
import {PIPELINE_CONFIG_SERVICE, pipelineConfigService} from 'core/pipeline/config/services/pipelineConfig.service';
import {Application} from 'core/application/application.model';
import {APPLICATION_MODEL_BUILDER, ApplicationModelBuilder} from 'core/application/applicationModel.builder';
import {IPipeline} from 'core/domain/IPipeline';

describe('CreatePipelineModal', () => {
  let $q: IQService;
  let $scope: IScope;
  let application: Application;
  let initializeComponent: (configs?: Partial<IPipeline>[]) => void;
  let component: CreatePipelineModal;

  beforeEach(
    mock.module(
      APPLICATION_MODEL_BUILDER,
      PIPELINE_CONFIG_SERVICE
    )
  );

  beforeEach(mock.inject((_$q_: IQService, $rootScope: IScope, applicationModelBuilder: ApplicationModelBuilder) => {
    $q = _$q_;
    $scope = $rootScope.$new();
    initializeComponent = (configs = []) => {
      application = applicationModelBuilder.createApplication(
        {
          key: 'pipelineConfigs',
          lazy: true,
          loader: () => $q.when(null),
          onLoad: () => $q.when(null),
        },
        {
          key: 'strategyConfigs',
          lazy: true,
          loader: () => $q.when(null),
          onLoad: () => $q.when(null),
        }
      );
      application.pipelineConfigs.data = configs;

      const props: ICreatePipelineModalProps = {
        application,
        show: true,
        showCallback: (): void => null,
        pipelineSavedCallback: (): void => null,
      };

      component = shallow(<CreatePipelineModal {...props}/>).instance() as CreatePipelineModal;
    };
  }));

  describe('template instantiation', () => {
    it('provides a default value when no templates exist', () => {
      initializeComponent();
      const template = component.state.templates[0];
      expect(component.state.templates.length).toBe(1);
      expect(template.name).toBe('None');
      expect(template.application).toBe('app');
      expect(template.triggers).toEqual([]);
      expect(template.stages).toEqual([]);
    });

    it('includes the default value when templates exist', () => {
      initializeComponent([{name: 'some pipeline'}]);
      expect(component.state.templates.length).toBe(2);
      expect(component.state.templates[0].name).toBe('None');
      expect(component.state.templates[1].name).toBe('some pipeline');
    });

    it('initializes command with the default template', () => {
      initializeComponent([ { name: 'some pipeline' } ]);
      expect(component.state.templates.length).toBe(2);
      expect(component.state.templates[0].name).toBe('None');
      expect(component.state.templates[1].name).toBe('some pipeline');
      expect(component.state.command.template.name).toBe('None');
    });

    it(`includes all config names in the component's state to be used to determine if a name is unique`, () => {
      initializeComponent([{ name: 'a'}, {name: 'b'}]);
      expect(component.state.templates.length).toBe(3);
      expect(component.state.existingNames).toEqual(['None', 'a', 'b']);
    });
  });

  describe('pipeline name validation', () => {
    const setPipelineName = (_component: CreatePipelineModal, name: string): void => {
      _component.setState({command: Object.assign({}, _component.state.command, {name})});
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
      initializeComponent([{ name: 'a'}, {name: 'b'}]);
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
        application.pipelineConfigs.data = [
          {name: 'new pipeline', id: '1234-5678'}
        ];
        return $q.when(null);
      });
      spyOn(pipelineConfigService, 'savePipeline').and.callFake((pipeline: IPipeline) => {
        submitted = pipeline;
        return $q.when(null);
      });

      component.setState({command: Object.assign({}, component.state.command, {name: 'new pipeline'})});

      component.submit();
      $scope.$digest();

      expect(submitted.name).toBe('new pipeline');
      expect(submitted.application).toBe('app');
      expect(submitted.stages).toEqual([]);
      expect(submitted.triggers).toEqual([]);
    });

    it('uses copy of plain version of pipeline', () => {
      let submitted: IPipeline = null;
      const  toCopy = {
        application: 'the_app',
        name: 'old_name',
        triggers: [{name: 'the_trigger', enabled: true, type: 'git'}]
      };
      initializeComponent([toCopy]);

      spyOn(application.pipelineConfigs, 'refresh').and.callFake(() => {
        application.pipelineConfigs.data = [{name: 'new pipeline', id: '1234-5678'}];
        return $q.when(null);
      });
      spyOn(pipelineConfigService, 'savePipeline').and.callFake((pipeline: IPipeline) => {
        submitted = pipeline;
        return $q.when(null);
      });

      component.state.command.name = 'new pipeline';
      component.state.command.template = toCopy;

      component.submit();
      $scope.$digest();

      expect(submitted.name).toBe('new pipeline');
      expect(submitted.application).toBe('the_app');
      expect(submitted.triggers.length).toBe(1);
    });

    it('should insert new pipeline as last one in application and set its index', () => {
      let submitted: IPipeline = null;
      initializeComponent([{name: 'x'}]);

      spyOn(application.pipelineConfigs, 'refresh').and.callFake(() => {
        application.pipelineConfigs.data = [{name: 'new pipeline', id: '1234-5678'}];
        return $q.when(null);
      });
      spyOn(pipelineConfigService, 'savePipeline').and.callFake((pipeline: IPipeline) => {
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
      spyOn(pipelineConfigService, 'savePipeline').and.callFake(() => {
        return $q.reject({data: {message: 'something went wrong'}});
      });

      component.submit();
      $scope.$digest();

      expect(component.state.saveError).toBe(true);
      expect(component.state.errorMessage).toBe('something went wrong');
    });

    it('provides default error message when none provided on failed save', () => {
      initializeComponent();
      spyOn(pipelineConfigService, 'savePipeline').and.callFake(() => {
        return $q.reject({});
      });

      component.submit();
      $scope.$digest();

      expect(component.state.saveError).toBe(true);
      expect(component.state.errorMessage).toBe('No message provided');
    });
  });
});
