import { mount, shallow } from 'enzyme';
import React from 'react';

import { ConfirmationModalService } from '../../confirmationModal';
import { setDirectRouter } from '../../navigation/directRouter';
import { Registry } from '../../registry/Registry';
import { ExecutionDetailsSectionNav, ExecutionDetailsSectionNavComponent } from './ExecutionDetailsSectionNav';
import { StageExecutionDetailsComponent } from './StageExecutionDetails';
import { StageFailureMessage } from './StageFailureMessage';
import { StageSummary } from './StageSummary';
import { StageSummaryWrapper, StageSummaryWrapperComponent } from './StageSummaryWrapper';
import { StepDetails } from './StepDetails';
import { StepExecutionDetailsWrapper, StepExecutionDetailsWrapperComponent } from './StepExecutionDetailsWrapper';
import { ExecutionStepDetails } from '../config/stages/common/ExecutionStepDetails';

describe('pipeline details bridge wrappers', () => {
  const routerProps = { router: {} as any, stateParams: {}, stateService: {} as any };
  const deckRuntimeServices = { executionService: {} } as any;

  afterEach(() => setDirectRouter(null));

  it('renders the direct StageSummaryWrapper for Angular summary templates', () => {
    const props = {
      application: {} as any,
      config: {},
      execution: {} as any,
      stage: {} as any,
      stageSummary: {} as any,
    };

    const component = shallow(<StageSummary {...props} />);

    expect(component.find(StageSummaryWrapper).length).toBe(1);
    expect(component.find(StageSummaryWrapper).prop('sourceUrl')).toBeDefined();
  });

  it('renders StageSummaryWrapper directly with step rows, markdown comments, and current step state', () => {
    const CustomStepLabel = ({ step }: any) => (
      <span className="custom-step-label">Custom {step.context.serverGroupName}</span>
    );
    spyOn(Registry.pipeline, 'getStageConfig').and.returnValue({
      executionStepLabelComponent: CustomStepLabel,
    } as any);
    const component = mount(
      <StageSummaryWrapperComponent
        {...routerProps}
        deckRuntimeServices={deckRuntimeServices}
        application={{ attributes: {} } as any}
        execution={{ stages: [] } as any}
        sourceUrl="template.html"
        stage={{ context: {}, type: 'deploy' } as any}
        stateParams={{ step: '1' }}
        stageSummary={
          {
            comments: '**approved**',
            name: 'Deploy',
            runningTimeInMs: 60000,
            stages: [
              { context: { serverGroupName: 'blue' }, name: 'first task', status: 'SUCCEEDED', type: 'task' },
              { context: { serverGroupName: 'green' }, name: 'second task', status: 'RUNNING', type: 'task' },
            ],
          } as any
        }
      />,
    );

    expect(component.find('tr.clickable').length).toBe(2);
    expect(component.find('tr.clickable').at(1).hasClass('info')).toBe(true);
    expect(component.find('.custom-step-label').at(0).text()).toBe('Custom blue');
    expect(component.find('.execution-details-comments').html()).toContain('<strong>approved</strong>');
  });

  it('sanitizes stage summary markdown comments', () => {
    const component = mount(
      <StageSummaryWrapperComponent
        {...routerProps}
        deckRuntimeServices={deckRuntimeServices}
        application={{ attributes: {} } as any}
        execution={{ stages: [] } as any}
        sourceUrl="template.html"
        stage={{ context: {}, type: 'deploy' } as any}
        stageSummary={
          {
            comments: '<img src=x onerror="alert(1)"> **safe** <script>alert(2)</script>',
            name: 'Deploy',
            stages: [],
          } as any
        }
      />,
    );

    const comments = component.find('.execution-details-comments').html();
    expect(comments).toContain('<strong>safe</strong>');
    expect(comments).not.toContain('onerror');
    expect(comments).not.toContain('<script>');
  });

  it('tracks the active execution details section from injected route params', () => {
    const component = shallow(
      <ExecutionDetailsSectionNavComponent
        {...({
          router: {},
          sections: ['firstSection', 'secondSection'],
          stateParams: { details: 'firstSection' },
          stateService: {},
        } as any)}
      />,
    );

    const activeSection = () =>
      component
        .find('Section')
        .filterWhere((section) => section.prop('active'))
        .prop('section');

    expect(activeSection()).toBe('firstSection');

    component.setProps({ stateParams: { details: 'secondSection' } } as any);

    expect(activeSection()).toBe('secondSection');
  });

  it('navigates execution details sections through the injected state service', () => {
    const injectedGo = jasmine.createSpy('injectedGo');

    const component = shallow(
      <ExecutionDetailsSectionNavComponent
        {...({
          router: {},
          sections: ['firstSection', 'secondSection'],
          stateParams: {},
          stateService: { go: injectedGo },
        } as any)}
      />,
    );

    component.find('Section').at(1).dive().find('a').simulate('click');

    expect(injectedGo).toHaveBeenCalledWith('.', { details: 'secondSection' });
  });

  it('resolves deep-linked stages through injected route params and state service', () => {
    const injectedGo = jasmine.createSpy('injectedGo');
    const firstSummary = { stages: [], type: 'wait' } as any;
    const secondSummary = {
      masterStage: { id: 'master-stage', type: 'deploy' },
      stages: [{ id: 'task-stage', type: 'deploy' }],
      type: 'deploy',
    } as any;

    const component = shallow(
      <StageExecutionDetailsComponent
        {...({ router: {}, stateParams: { stageId: 'task-stage' }, stateService: { go: injectedGo } } as any)}
        application={{} as any}
        execution={{ stageSummaries: [firstSummary, secondSummary] } as any}
      />,
      { disableLifecycleMethods: true },
    );

    expect(injectedGo).toHaveBeenCalledWith(
      '.',
      { stage: 1, subStage: undefined, step: 0, stageId: null },
      { location: 'replace' },
    );
    expect(component.find(StageSummary).prop('stageSummary')).toBe(secondSummary);
  });

  it('selects the next routed stage immediately when route props change', () => {
    const firstSummary = {
      masterStage: { id: 'first-master', type: 'deploy' },
      stages: [{ id: 'first-task', type: 'deploy' }],
      type: 'deploy',
    } as any;
    const secondSummary = {
      masterStage: { id: 'second-master', type: 'deploy' },
      stages: [{ id: 'second-task', type: 'deploy' }],
      type: 'deploy',
    } as any;
    const component = shallow(
      <StageExecutionDetailsComponent
        {...({
          router: {},
          stateParams: { stageId: 'first-task' },
          stateService: { go: jasmine.createSpy('go') },
        } as any)}
        application={{} as any}
        execution={{ stageSummaries: [{ stages: [] }, firstSummary, secondSummary] } as any}
      />,
      { disableLifecycleMethods: true },
    );

    component.setProps({ stateParams: { stageId: 'second-task' } } as any);

    expect(component.find(StageSummary).prop('stageSummary')).toBe(secondSummary);
  });

  it('waits for routed props before selecting a stage from a different execution', () => {
    const injectedGo = jasmine.createSpy('injectedGo');
    const firstSummary = {
      masterStage: { id: 'first-master', type: 'deploy' },
      stages: [{ id: 'first-task', type: 'deploy' }],
      type: 'deploy',
    } as any;
    const secondSummary = {
      masterStage: { id: 'second-master', type: 'deploy' },
      stages: [{ id: 'second-task', type: 'deploy' }],
      type: 'deploy',
    } as any;
    const firstExecution = { id: 'first-execution', stageSummaries: [firstSummary] } as any;
    const secondExecution = { id: 'second-execution', stageSummaries: [{ stages: [] }, secondSummary] } as any;
    const initialProps = {
      application: {} as any,
      execution: firstExecution,
      router: {},
      stateParams: { executionId: 'first-execution', stage: '0', step: '0' },
      stateService: { go: injectedGo },
    } as any;

    const component = shallow(<StageExecutionDetailsComponent {...initialProps} />, { disableLifecycleMethods: true });
    const instance = component.instance() as StageExecutionDetailsComponent;

    injectedGo.calls.reset();
    instance.componentWillReceiveProps({
      ...initialProps,
      stateParams: { executionId: 'second-execution', stage: '1', step: '0' },
    });

    expect(injectedGo).not.toHaveBeenCalled();

    component.setProps({
      execution: secondExecution,
      stateParams: { executionId: 'second-execution', stage: '1', step: '0' },
    });

    expect(component.find(StageSummary).prop('stageSummary')).toBe(secondSummary);
  });

  it('toggles stage summary details through the injected router and preserves stage indices', () => {
    const injectedGo = jasmine.createSpy('injectedGo');

    const component = mount(
      <StageSummaryWrapperComponent
        {...routerProps}
        deckRuntimeServices={deckRuntimeServices}
        application={{ attributes: {} } as any}
        execution={{ stages: [] } as any}
        sourceUrl="template.html"
        stage={{ context: {}, type: 'deploy' } as any}
        stageSummary={
          {
            stages: [
              { name: 'first task', status: 'SUCCEEDED' },
              { name: 'second task', status: 'RUNNING' },
            ],
          } as any
        }
        stateParams={{ stage: '2', subStage: '3', step: '0' }}
        stateService={{ go: injectedGo } as any}
      />,
    );

    component.find('tr.clickable').at(1).simulate('click');

    expect(injectedGo).toHaveBeenCalledWith('.', { stage: 2, step: 1, subStage: 3 });
  });

  it('confirms manual skip against the top-level stage', async () => {
    const updatedExecution = { stages: [{ id: 'parent', status: 'SKIPPED' }] };
    const executionService = {
      patchExecution: jasmine.createSpy('patchExecution').and.returnValue(Promise.resolve(null)),
      updateExecution: jasmine.createSpy('updateExecution').and.returnValue(Promise.resolve(null)),
      waitUntilExecutionMatches: jasmine
        .createSpy('waitUntilExecutionMatches')
        .and.returnValue(Promise.resolve(updatedExecution)),
    };
    spyOn(ConfirmationModalService, 'confirm').and.callFake((config: any) => config.submitMethod('operator reason'));

    const component = mount(
      <StageSummaryWrapperComponent
        {...routerProps}
        deckRuntimeServices={{ executionService } as any}
        application={{ attributes: {} } as any}
        execution={
          {
            id: 'execution-id',
            stages: [{ id: 'parent', context: { canManuallySkip: true }, name: 'Parent' }],
          } as any
        }
        sourceUrl="template.html"
        stage={{ id: 'child', context: {}, isRunning: true, parentStageId: 'parent', type: 'deploy' } as any}
        stageSummary={{ name: 'Child', stages: [] } as any}
      />,
    );

    component.find('button.manual-skip').simulate('click');
    await (ConfirmationModalService.confirm as jasmine.Spy).calls.mostRecent().returnValue;

    expect(executionService.patchExecution).toHaveBeenCalledWith('execution-id', 'parent', {
      manualSkip: true,
      reason: 'operator reason',
    });
    expect(executionService.updateExecution).toHaveBeenCalledWith({ attributes: {} } as any, updatedExecution);
  });

  it('renders the direct StepExecutionDetailsWrapper for Angular detail templates', () => {
    const props = {
      application: {} as any,
      config: { cloudProvider: 'aws' },
      execution: {} as any,
      stage: {} as any,
    };

    const component = shallow(<StepDetails {...props} />);

    expect(component.find(StepExecutionDetailsWrapper).length).toBe(1);
    expect(component.find(StepExecutionDetailsWrapper).prop('sourceUrl')).toBeDefined();
  });

  it('renders StepExecutionDetailsWrapper default execution details without section nav', () => {
    const component = shallow(
      <StepExecutionDetailsWrapperComponent
        {...routerProps}
        application={{} as any}
        configSections={['Task Status']}
        execution={{} as any}
        sourceUrl="template.html"
        stage={{ failureMessage: 'it failed', tasks: [{ name: 'deploy', status: 'SUCCEEDED' }] } as any}
      />,
    );

    expect(component.find(ExecutionDetailsSectionNav).exists()).toBe(false);
    expect(component.find(ExecutionStepDetails).prop('item')).toEqual({
      failureMessage: 'it failed',
      tasks: [{ name: 'deploy', status: 'SUCCEEDED' }],
    } as any);
    expect(component.find(StageFailureMessage).prop('message')).toBe('it failed');
  });

  it('renders custom StepExecutionDetailsWrapper execution details component', () => {
    const CustomExecutionDetails = ({ stage }: any) => (
      <div className="custom-execution-details">Custom details for {stage.context.serverGroupName}</div>
    );

    const component = shallow(
      <StepExecutionDetailsWrapperComponent
        {...routerProps}
        application={{} as any}
        config={{ executionDetailsComponent: CustomExecutionDetails } as any}
        configSections={['Custom']}
        execution={{} as any}
        provider="aws"
        sourceUrl="template.html"
        stage={{ context: { serverGroupName: 'my-server-group' }, failureMessage: 'it failed', tasks: [] } as any}
      />,
    );

    expect(component.find(CustomExecutionDetails).prop('stage')).toEqual({
      context: { serverGroupName: 'my-server-group' },
      failureMessage: 'it failed',
      tasks: [],
    } as any);
    expect(component.find(ExecutionStepDetails).exists()).toBe(false);
  });

  it('passes the injected details route param to custom step execution details', () => {
    const params = { details: 'facade-details' };
    setDirectRouter({ globals: { params }, stateService: { params } } as any);
    const CustomExecutionDetails = () => null;

    const component = shallow(
      <StepExecutionDetailsWrapperComponent
        {...routerProps}
        {...({
          application: {},
          config: { executionDetailsComponent: CustomExecutionDetails },
          configSections: ['Custom'],
          execution: {},
          sourceUrl: 'template.html',
          stage: {},
          stateParams: { details: 'injected-details' },
        } as any)}
      />,
    );

    expect(component.find(CustomExecutionDetails).prop('currentSection')).toBe('injected-details');
  });

  it('passes config sections to custom StepExecutionDetailsWrapper components so they can render section nav', () => {
    const CustomExecutionDetails = ({ configSections }: any) => (
      <ExecutionDetailsSectionNav sections={configSections} />
    );

    const component = shallow(
      <StepExecutionDetailsWrapperComponent
        {...routerProps}
        application={{} as any}
        config={{ executionDetailsComponent: CustomExecutionDetails } as any}
        configSections={['Custom', 'Tasks']}
        execution={{} as any}
        sourceUrl="template.html"
        stage={{ context: {}, tasks: [] } as any}
      />,
    );

    expect(component.find(CustomExecutionDetails).prop('configSections')).toEqual(['Custom', 'Tasks']);
    expect(component.find(CustomExecutionDetails).dive().find(ExecutionDetailsSectionNav).prop('sections')).toEqual([
      'Custom',
      'Tasks',
    ]);
  });
});
