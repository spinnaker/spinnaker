import { mount, shallow } from 'enzyme';
import React from 'react';

import { AngularServices } from '../../angular/services';
import { ConfirmationModalService } from '../../confirmationModal';
import { Registry } from '../../registry/Registry';
import { ExecutionDetailsSectionNav } from './ExecutionDetailsSectionNav';
import { StageFailureMessage } from './StageFailureMessage';
import { StageSummary } from './StageSummary';
import { StageSummaryWrapper } from './StageSummaryWrapper';
import { StepDetails } from './StepDetails';
import { StepExecutionDetailsWrapper } from './StepExecutionDetailsWrapper';
import { ExecutionStepDetails } from '../config/stages/common/ExecutionStepDetails';

describe('pipeline details bridge wrappers', () => {
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
    spyOnProperty(AngularServices, '$stateParams', 'get').and.returnValue({ step: '1' } as any);

    const component = mount(
      <StageSummaryWrapper
        application={{ attributes: {} } as any}
        execution={{ stages: [] } as any}
        sourceUrl="template.html"
        stage={{ context: {}, type: 'deploy' } as any}
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
    spyOnProperty(AngularServices, '$stateParams', 'get').and.returnValue({} as any);

    const component = mount(
      <StageSummaryWrapper
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

  it('toggles stage summary details through UI Router params and preserves stage indices', () => {
    const go = jasmine.createSpy('go');
    spyOnProperty(AngularServices, '$stateParams', 'get').and.returnValue({
      stage: '2',
      subStage: '3',
      step: '0',
    } as any);
    spyOnProperty(AngularServices, '$state', 'get').and.returnValue({ go } as any);

    const component = mount(
      <StageSummaryWrapper
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
      />,
    );

    component.find('tr.clickable').at(1).simulate('click');

    expect(go).toHaveBeenCalledWith('.', { stage: 2, step: 1, subStage: 3 });
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
    spyOnProperty(AngularServices, 'executionService', 'get').and.returnValue(executionService as any);

    const component = mount(
      <StageSummaryWrapper
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
      <StepExecutionDetailsWrapper
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
      <StepExecutionDetailsWrapper
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

  it('passes config sections to custom StepExecutionDetailsWrapper components so they can render section nav', () => {
    const CustomExecutionDetails = ({ configSections }: any) => (
      <ExecutionDetailsSectionNav sections={configSections} />
    );

    const component = shallow(
      <StepExecutionDetailsWrapper
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
