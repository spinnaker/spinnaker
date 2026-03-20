import { mount, shallow } from 'enzyme';
import React from 'react';

import { PipelineTriggerTemplate } from './PipelineTriggerTemplate';
import type { IExecution, IPipelineCommand, IPipelineTrigger } from '../../../../domain';
import { ReactInjector } from '../../../../reactShims';
import { ExecutionsTransformer } from '../../../service/ExecutionsTransformer';

/**
 * PipelineTriggerTemplate - execution selector for pipeline triggers.
 *
 * Responsibilities:
 * - Fetch and display available pipeline executions as dropdown options
 * - Allow user to select which execution to use as the trigger source
 * - Persist selection when unrelated form fields change (Formik creates new object refs)
 * - Extract fields from parentExecution for re-run scenarios
 */
describe('<PipelineTriggerTemplate />', () => {
  let getExecutionsForConfigIdsSpy: jasmine.Spy;
  let addBuildInfoSpy: jasmine.Spy;

  // Higher buildNumber = older execution (used for buildTime calculation)
  const createExecution = (id: string, buildNumber: number, overrides: Partial<IExecution> = {}): IExecution =>
    ({
      id,
      application: 'test-app',
      buildTime: Date.now() - buildNumber * 1000,
      status: 'SUCCEEDED',
      pipelineConfigId: 'source-pipeline-id',
      name: 'Source Pipeline',
      stages: [],
      trigger: { type: 'manual' } as any,
      ...overrides,
    } as IExecution);

  const execution1 = createExecution('exec-1', 1); // most recent
  const execution2 = createExecution('exec-2', 2);
  const execution3 = createExecution('exec-3', 3); // oldest

  // pipeline = source pipeline config ID that this trigger watches
  // parentPipelineId = specific execution ID to pre-select (used in re-runs)
  const createPipelineTrigger = (pipelineId: string, parentPipelineId?: string): IPipelineTrigger =>
    ({
      enabled: true,
      type: 'pipeline',
      application: 'source-app',
      pipeline: pipelineId,
      parentPipelineId,
      status: ['successful'],
    } as IPipelineTrigger);

  // The component MUTATES command.extraFields and command.triggerInvalid directly.
  // This is the interface contract with ManualPipelineExecutionModal.
  const createCommand = (trigger: IPipelineTrigger): IPipelineCommand => ({
    pipeline: {
      application: 'test-app',
      id: 'pipeline-id',
      name: 'Test Pipeline',
      stages: [],
      triggers: [trigger],
      parameterConfig: [],
      keepWaitingPipelines: false,
      limitConcurrent: true,
    },
    trigger,
    triggerInvalid: false,
    extraFields: {},
    notificationEnabled: false,
    notification: { type: 'email', address: '', when: [] },
    pipelineName: 'Test Pipeline',
  });

  const updateCommandSpy = jasmine.createSpy('updateCommand');

  beforeEach(() => {
    getExecutionsForConfigIdsSpy = jasmine.createSpy('getExecutionsForConfigIds');
    // ReactInjector.executionService is a getter, not a method - use spyOnProperty
    spyOnProperty(ReactInjector, 'executionService', 'get').and.returnValue({
      getExecutionsForConfigIds: getExecutionsForConfigIdsSpy,
    });
    addBuildInfoSpy = spyOn(ExecutionsTransformer, 'addBuildInfo');
    updateCommandSpy.calls.reset();
  });

  // shallow() renders only the component, not children - faster, good for unit tests
  // mount() renders full DOM tree - needed when testing interactions or lifecycle
  describe('Component lifecycle', () => {
    it('displays loading spinner while fetching executions', () => {
      // Promise that never resolves keeps component in loading state
      getExecutionsForConfigIdsSpy.and.returnValue(new Promise(() => {}));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      const wrapper = shallow(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      expect(wrapper.find('Spinner').exists()).toBe(true);
    });

    it('displays error message on load failure', async () => {
      getExecutionsForConfigIdsSpy.and.returnValue(Promise.reject(new Error('Load failed')));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      const wrapper = shallow(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      // await Promise.resolve() flushes the microtask queue, allowing the
      // component's promise callbacks to execute before we check state
      await Promise.resolve();
      wrapper.update(); // sync enzyme wrapper with React component state

      expect(wrapper.text()).toContain('Error loading executions');
    });

    it('displays "No recent executions found" when list is empty', async () => {
      getExecutionsForConfigIdsSpy.and.returnValue(Promise.resolve([]));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      const wrapper = shallow(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await Promise.resolve();
      wrapper.update();

      expect(wrapper.text()).toContain('No recent executions found');
    });

    it('renders execution dropdown with correct options after load', async () => {
      const executions = [execution1, execution2, execution3];
      getExecutionsForConfigIdsSpy.and.returnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      const wrapper = shallow(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await Promise.resolve();
      wrapper.update();

      expect(wrapper.find('TetheredSelect').exists()).toBe(true);
      const options = wrapper.find('TetheredSelect').prop('options') as Array<{ value: string }>;
      expect(options.length).toBe(3);
      expect(options.map((o) => o.value)).toEqual(['exec-1', 'exec-2', 'exec-3']);
    });
  });

  describe('Execution selection preservation', () => {
    it('preserves selection when command object reference changes but trigger.pipeline is unchanged', async () => {
      const executions = [execution1, execution2, execution3];
      getExecutionsForConfigIdsSpy.and.returnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      // mount() needed to access instance methods and component state
      const wrapper = mount(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await Promise.resolve();
      wrapper.update();

      expect(wrapper.state('selectedExecution')).toBe('exec-1');

      // Calling handleExecutionChanged directly instead of simulating DOM events.
      // Trade-off: faster/simpler tests but doesn't verify event wiring.
      const instance = wrapper.instance() as PipelineTriggerTemplate;
      (instance as any).handleExecutionChanged({ value: 'exec-2' });
      wrapper.update();

      expect(wrapper.state('selectedExecution')).toBe('exec-2');

      // Simulating Formik behavior: when user types in any form field,
      // Formik creates a NEW command object via spread operator.
      // The object reference changes but trigger.pipeline value stays same.
      const newCommand = {
        ...command,
        parameters: { changeNumber: 'CHG000123' },
      };

      getExecutionsForConfigIdsSpy.calls.reset();
      wrapper.setProps({ command: newCommand });
      wrapper.update();

      // Key assertion: API should NOT be called again since pipeline didn't change
      expect(getExecutionsForConfigIdsSpy).not.toHaveBeenCalled();
      expect(wrapper.state('selectedExecution')).toBe('exec-2');
    });

    it('preserves user selection after initial load completes', async () => {
      const executions = [execution1, execution2, execution3];
      getExecutionsForConfigIdsSpy.and.returnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      const wrapper = mount(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await Promise.resolve();
      wrapper.update();

      const instance = wrapper.instance() as PipelineTriggerTemplate;
      (instance as any).handleExecutionChanged({ value: 'exec-3' });
      wrapper.update();

      expect(wrapper.state('selectedExecution')).toBe('exec-3');
      expect(command.extraFields.parentPipelineId).toBe('exec-3');
    });
  });

  describe('Re-initialization behavior', () => {
    it('refetches executions when trigger.pipeline changes', async () => {
      const executions = [execution1, execution2];
      getExecutionsForConfigIdsSpy.and.returnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      const wrapper = mount(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await Promise.resolve();
      wrapper.update();

      getExecutionsForConfigIdsSpy.calls.reset();

      const newTrigger = createPipelineTrigger('different-pipeline-id');
      const newCommand = createCommand(newTrigger);
      wrapper.setProps({ command: newCommand });

      expect(getExecutionsForConfigIdsSpy).toHaveBeenCalledWith(['different-pipeline-id'], { limit: 20 });
    });

    it('defaults to latest execution when parentPipelineId does not match', async () => {
      const executions = [execution1, execution2, execution3];
      getExecutionsForConfigIdsSpy.and.returnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id', 'non-existent-id');
      const command = createCommand(trigger);

      const wrapper = shallow(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await Promise.resolve();
      wrapper.update();

      expect(wrapper.state('selectedExecution')).toBe('exec-1');
    });

    it('selects matching execution when parentPipelineId exists in list', async () => {
      const executions = [execution1, execution2, execution3];
      getExecutionsForConfigIdsSpy.and.returnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id', 'exec-2');
      const command = createCommand(trigger);

      const wrapper = shallow(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await Promise.resolve();
      wrapper.update();

      expect(wrapper.state('selectedExecution')).toBe('exec-2');
    });
  });

  // Re-run = user clicks "Start execution with same parameters" on an existing execution.
  // In this case, trigger.parentExecution contains the original execution's data,
  // but trigger.pipeline/application may be unset. The component extracts these fields.
  describe('Re-run scenario', () => {
    it('extracts fields from parentExecution', async () => {
      const executions = [execution1, execution2];
      getExecutionsForConfigIdsSpy.and.returnValue(Promise.resolve(executions));

      const parentExecution: Partial<IExecution> = {
        id: 'parent-exec-id',
        application: 'parent-app',
        pipelineConfigId: 'parent-pipeline-config-id',
      };

      // Note: trigger.pipeline and trigger.application are NOT set initially.
      // The component's initialize() method copies them from parentExecution.
      const trigger: IPipelineTrigger = {
        enabled: true,
        type: 'pipeline',
        parentExecution: parentExecution as IExecution,
        status: ['successful'],
      } as IPipelineTrigger;

      const command = createCommand(trigger);

      shallow(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      // The component MUTATES the trigger object to populate these fields
      expect(trigger.application).toBe('parent-app');
      expect(trigger.pipeline).toBe('parent-pipeline-config-id');
      expect(trigger.parentPipelineId).toBe('parent-exec-id');
      expect(getExecutionsForConfigIdsSpy).toHaveBeenCalledWith(['parent-pipeline-config-id'], { limit: 20 });
    });
  });

  describe('User interaction', () => {
    it('updates extraFields when user changes execution selection', async () => {
      const executions = [execution1, execution2];
      getExecutionsForConfigIdsSpy.and.returnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      const wrapper = mount(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await Promise.resolve();
      wrapper.update();

      expect(command.extraFields.parentPipelineId).toBe('exec-1');
      expect(command.triggerInvalid).toBe(false);

      const instance = wrapper.instance() as PipelineTriggerTemplate;
      (instance as any).handleExecutionChanged({ value: 'exec-2' });

      expect(command.extraFields.parentPipelineId).toBe('exec-2');
      expect(command.extraFields.parentPipelineApplication).toBe('test-app');
    });

    it('sets triggerInvalid to false after successful execution selection', async () => {
      const executions = [execution1];
      getExecutionsForConfigIdsSpy.and.returnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);
      command.triggerInvalid = true; // Start with invalid

      const wrapper = shallow(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await Promise.resolve();
      wrapper.update();

      // After successful load and selection, trigger should be valid
      expect(command.triggerInvalid).toBe(false);
    });

    it('handles multiple rapid selection changes correctly', async () => {
      const executions = [execution1, execution2, execution3];
      getExecutionsForConfigIdsSpy.and.returnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      const wrapper = mount(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await Promise.resolve();
      wrapper.update();

      const instance = wrapper.instance() as PipelineTriggerTemplate;

      // Rapidly change selections
      (instance as any).handleExecutionChanged({ value: 'exec-2' });
      (instance as any).handleExecutionChanged({ value: 'exec-3' });
      (instance as any).handleExecutionChanged({ value: 'exec-1' });

      // Final selection should be the last one
      expect(wrapper.state('selectedExecution')).toBe('exec-1');
      expect(command.extraFields.parentPipelineId).toBe('exec-1');
    });
  });

  describe('Edge cases', () => {
    it('handles trigger with undefined pipeline gracefully', async () => {
      getExecutionsForConfigIdsSpy.and.returnValue(Promise.resolve([]));

      const trigger = ({
        enabled: true,
        type: 'pipeline',
        application: 'source-app',
        pipeline: undefined,
        status: ['successful'],
      } as unknown) as IPipelineTrigger;

      const command = createCommand(trigger);

      expect(() => {
        shallow(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);
      }).not.toThrow();

      expect(getExecutionsForConfigIdsSpy).toHaveBeenCalledWith([undefined], { limit: 20 });
    });

    it('handles trigger type change from pipeline to non-pipeline', async () => {
      const executions = [execution1, execution2];
      getExecutionsForConfigIdsSpy.and.returnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      const wrapper = mount(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await Promise.resolve();
      wrapper.update();

      expect(wrapper.state('selectedExecution')).toBe('exec-1');

      getExecutionsForConfigIdsSpy.calls.reset();

      const manualTrigger = { type: 'manual', enabled: true } as any;
      const newCommand = { ...command, trigger: manualTrigger };
      wrapper.setProps({ command: newCommand });

      expect(getExecutionsForConfigIdsSpy).not.toHaveBeenCalled();
    });

    it('handles empty pipeline ID string', async () => {
      getExecutionsForConfigIdsSpy.and.returnValue(Promise.resolve([]));

      const trigger = createPipelineTrigger('');
      const command = createCommand(trigger);

      const wrapper = shallow(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await Promise.resolve();
      wrapper.update();

      expect(getExecutionsForConfigIdsSpy).toHaveBeenCalledWith([''], { limit: 20 });
      expect(wrapper.text()).toContain('No recent executions found');
    });

    it('handles executions with various status values', async () => {
      const successExec = createExecution('exec-success', 1, { status: 'SUCCEEDED' });
      const failedExec = createExecution('exec-failed', 2, { status: 'TERMINAL' });
      const runningExec = createExecution('exec-running', 3, { status: 'RUNNING' });
      const canceledExec = createExecution('exec-canceled', 4, { status: 'CANCELED' });

      const executions = [successExec, failedExec, runningExec, canceledExec];
      getExecutionsForConfigIdsSpy.and.returnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      const wrapper = shallow(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await Promise.resolve();
      wrapper.update();

      const options = wrapper.find('TetheredSelect').prop('options') as Array<{ value: string }>;
      expect(options.length).toBe(4);
      expect(options.map((o) => o.value)).toEqual(['exec-success', 'exec-failed', 'exec-running', 'exec-canceled']);
    });
  });

  describe('Async behavior', () => {
    // TODO: Fix potential race condition - if user switches pipelines while a request is loading,
    // the old request can return after the new one and show the wrong executions.
    // Fix would involve something like tracking which request is current and ignoring stale responses.
    it('late-arriving response overwrites current state (known issue)', async () => {
      let resolveFirst: (value: IExecution[]) => void = () => {};
      let resolveSecond: (value: IExecution[]) => void = () => {};

      const firstPromise = new Promise<IExecution[]>((resolve) => {
        resolveFirst = resolve;
      });
      const secondPromise = new Promise<IExecution[]>((resolve) => {
        resolveSecond = resolve;
      });

      getExecutionsForConfigIdsSpy.and.returnValues(firstPromise, secondPromise);

      const trigger = createPipelineTrigger('pipeline-1');
      const command = createCommand(trigger);

      const wrapper = mount(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      // User switches to pipeline-2 before pipeline-1 request finishes
      const newTrigger = createPipelineTrigger('pipeline-2');
      const newCommand = createCommand(newTrigger);
      wrapper.setProps({ command: newCommand });

      // Pipeline-2 response arrives first (as expected)
      const pipeline2Executions = [createExecution('exec-p2-1', 1)];
      resolveSecond(pipeline2Executions);
      await Promise.resolve();
      wrapper.update();

      expect(wrapper.state('selectedExecution')).toBe('exec-p2-1');

      // Pipeline-1 response arrives late - this is the problem
      const pipeline1Executions = [createExecution('exec-p1-1', 1)];
      resolveFirst(pipeline1Executions);
      await Promise.resolve();
      wrapper.update();

      // Current behavior: late response overwrites the correct data
      // Expected behavior (when fixed): should still show exec-p2-1
      expect(wrapper.state('selectedExecution')).toBe('exec-p1-1');
    });

    it('maintains loading state until promise resolves', async () => {
      let resolvePromise: (value: IExecution[]) => void = () => {};
      const pendingPromise = new Promise<IExecution[]>((resolve) => {
        resolvePromise = resolve;
      });

      getExecutionsForConfigIdsSpy.and.returnValue(pendingPromise);

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      const wrapper = shallow(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      expect(wrapper.state('executionsLoading')).toBe(true);
      expect(wrapper.find('Spinner').exists()).toBe(true);

      resolvePromise([execution1]);
      await Promise.resolve();
      wrapper.update();

      expect(wrapper.state('executionsLoading')).toBe(false);
      expect(wrapper.find('Spinner').exists()).toBe(false);
    });

    it('calls addBuildInfo for each execution after load', async () => {
      const executions = [execution1, execution2, execution3];
      getExecutionsForConfigIdsSpy.and.returnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      shallow(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await Promise.resolve();

      expect(addBuildInfoSpy).toHaveBeenCalledTimes(3);
      expect(addBuildInfoSpy).toHaveBeenCalledWith(execution1);
      expect(addBuildInfoSpy).toHaveBeenCalledWith(execution2);
      expect(addBuildInfoSpy).toHaveBeenCalledWith(execution3);
    });
  });

  describe('State consistency', () => {
    it('maintains state after multiple prop updates without pipeline change', async () => {
      const executions = [execution1, execution2, execution3];
      getExecutionsForConfigIdsSpy.and.returnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      const wrapper = mount(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await Promise.resolve();
      wrapper.update();

      const instance = wrapper.instance() as PipelineTriggerTemplate;
      (instance as any).handleExecutionChanged({ value: 'exec-2' });

      for (let i = 0; i < 5; i++) {
        const updatedCommand = {
          ...command,
          parameters: { changeNumber: `CHG00${i}` },
        };
        wrapper.setProps({ command: updatedCommand });
        wrapper.update();
      }

      expect(wrapper.state('selectedExecution')).toBe('exec-2');
      expect(wrapper.state('executions')).toEqual(executions);
      expect(wrapper.state('executionsLoading')).toBe(false);
      expect(wrapper.state('loadError')).toBe(false);
    });

    it('loads new executions when pipeline changes', async () => {
      const pipeline1Executions = [createExecution('p1-exec-1', 1), createExecution('p1-exec-2', 2)];

      const pipeline2Executions = [
        createExecution('p2-exec-1', 1),
        createExecution('p2-exec-2', 2),
        createExecution('p2-exec-3', 3),
      ];

      getExecutionsForConfigIdsSpy.and.returnValues(
        Promise.resolve(pipeline1Executions),
        Promise.resolve(pipeline2Executions),
      );

      const trigger = createPipelineTrigger('pipeline-1');
      const command = createCommand(trigger);

      const wrapper = mount(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await Promise.resolve();
      wrapper.update();

      expect(wrapper.state('selectedExecution')).toBe('p1-exec-1');
      expect((wrapper.state('executions') as IExecution[]).length).toBe(2);

      const instance = wrapper.instance() as PipelineTriggerTemplate;
      (instance as any).handleExecutionChanged({ value: 'p1-exec-2' });
      expect(wrapper.state('selectedExecution')).toBe('p1-exec-2');

      const newTrigger = createPipelineTrigger('pipeline-2');
      const newCommand = createCommand(newTrigger);
      wrapper.setProps({ command: newCommand });

      await Promise.resolve();
      wrapper.update();

      expect(wrapper.state('selectedExecution')).toBe('p2-exec-1');
      expect((wrapper.state('executions') as IExecution[]).length).toBe(3);
    });

    it('clears extraFields when pipeline changes', async () => {
      const executions = [execution1, execution2];
      getExecutionsForConfigIdsSpy.and.returnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('pipeline-1');
      const command = createCommand(trigger);

      const wrapper = mount(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await Promise.resolve();
      wrapper.update();

      expect(command.extraFields.parentPipelineId).toBe('exec-1');

      const newTrigger = createPipelineTrigger('pipeline-2');
      const newCommand = createCommand(newTrigger);
      wrapper.setProps({ command: newCommand });

      expect(newCommand.extraFields).toEqual({});
    });
  });

  describe('Formik integration', () => {
    it('preserves selection through typical form interaction sequence', async () => {
      const executions = [execution1, execution2, execution3];
      getExecutionsForConfigIdsSpy.and.returnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      const wrapper = mount(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await Promise.resolve();
      wrapper.update();
      expect(wrapper.state('selectedExecution')).toBe('exec-1');

      const instance = wrapper.instance() as PipelineTriggerTemplate;
      (instance as any).handleExecutionChanged({ value: 'exec-3' });
      wrapper.update();
      expect(wrapper.state('selectedExecution')).toBe('exec-3');

      // Simulate typing "CHG123456" one character at a time.
      // Each keystroke causes Formik to create new objects via spread:
      //   { ...command, trigger: { ...trigger }, ... }
      // This is why we spread trigger too - Formik does this internally.
      const changeNumberChars = 'CHG123456';
      for (let i = 1; i <= changeNumberChars.length; i++) {
        const newCommand = {
          ...command,
          trigger: { ...trigger }, // new object, but trigger.pipeline value unchanged
          parameters: { changeNumber: changeNumberChars.substring(0, i) },
        };

        getExecutionsForConfigIdsSpy.calls.reset();
        wrapper.setProps({ command: newCommand });
        wrapper.update();

        expect(getExecutionsForConfigIdsSpy).not.toHaveBeenCalled();
        expect(wrapper.state('selectedExecution')).toBe('exec-3');
      }

      expect(wrapper.state('selectedExecution')).toBe('exec-3');
      expect(command.extraFields.parentPipelineId).toBe('exec-3');
    });

    it('handles simultaneous parameter and trigger changes correctly', async () => {
      const executions = [execution1, execution2];
      getExecutionsForConfigIdsSpy.and.returnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('pipeline-1');
      const command = createCommand(trigger);

      const wrapper = mount(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await Promise.resolve();
      wrapper.update();

      getExecutionsForConfigIdsSpy.calls.reset();

      const newTrigger = createPipelineTrigger('pipeline-2');
      const newCommand = {
        ...createCommand(newTrigger),
        parameters: { newParam: 'value' },
      };
      wrapper.setProps({ command: newCommand });

      expect(getExecutionsForConfigIdsSpy).toHaveBeenCalledWith(['pipeline-2'], { limit: 20 });
    });
  });

  describe('Rendering', () => {
    it('renders with form-group structure and label', async () => {
      const executions = [execution1];
      getExecutionsForConfigIdsSpy.and.returnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      const wrapper = shallow(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await Promise.resolve();
      wrapper.update();

      expect(wrapper.find('.form-group').exists()).toBe(true);
      expect(wrapper.find('label').text()).toBe('Execution');
    });

    it('renders all executions as dropdown options', async () => {
      const manyExecutions = Array.from({ length: 15 }, (_, i) => createExecution(`exec-${i}`, i));

      getExecutionsForConfigIdsSpy.and.returnValue(Promise.resolve(manyExecutions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      const wrapper = shallow(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await Promise.resolve();
      wrapper.update();

      const options = wrapper.find('TetheredSelect').prop('options') as Array<{ value: string }>;
      expect(options.length).toBe(15);
    });

    it('dropdown is not clearable', async () => {
      const executions = [execution1];
      getExecutionsForConfigIdsSpy.and.returnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      const wrapper = shallow(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await Promise.resolve();
      wrapper.update();

      expect(wrapper.find('TetheredSelect').prop('clearable')).toBe(false);
    });
  });
});
