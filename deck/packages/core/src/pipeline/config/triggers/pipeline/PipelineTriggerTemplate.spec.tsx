import { render, screen, waitFor } from '@testing-library/react';
import React from 'react';
import type { Mock } from 'vitest';

import { PipelineTriggerTemplateComponent } from './PipelineTriggerTemplate';
import type { DeckRuntimeServices } from '../../../../bootstrap';
import { SETTINGS } from '../../../../config/settings';
import type { IExecution, IPipelineCommand, IPipelineTrigger } from '../../../../domain';
import { ExecutionsTransformer } from '../../../service/ExecutionsTransformer';
import { setupUser } from '../../../../utils/testUtils/userEvent';

/**
 * PipelineTriggerTemplate - execution selector for pipeline triggers.
 *
 * Responsibilities:
 * - Fetch and display available pipeline executions as dropdown options
 * - Allow user to select which execution to use as the trigger source
 * - Persist selection when unrelated form fields change (Formik creates new object refs)
 * - Extract fields from parentExecution for re-run scenarios
 *
 * Migration notes (React Testing Library):
 * - The component mutates `command.extraFields`/`command.triggerInvalid`/`command.trigger`
 *   directly as its interface contract with ManualPipelineExecutionModal. The selected
 *   execution id is written to `command.extraFields.parentPipelineId`, which mirrors the
 *   component's internal `selectedExecution` state. Tests therefore assert on the command
 *   object (observable behaviour) rather than reaching into component state.
 * - Loading/error/empty states are asserted via rendered DOM (spinner element, text).
 * - Selection changes are driven through the rendered react-select dropdown
 *   (open the combobox, click an option) rather than by calling private methods.
 * - Prop updates use the `rerender` helper returned by `render`.
 */
describe('<PipelineTriggerTemplate />', () => {
  let getExecutionsForConfigIdsSpy: Mock;
  let addBuildInfoSpy: Mock;

  class PipelineTriggerTemplate extends PipelineTriggerTemplateComponent {
    public static defaultProps = {
      deckRuntimeServices: ({
        executionService: {
          getExecutionsForConfigIds: (...args: unknown[]) => getExecutionsForConfigIdsSpy(...args),
        },
      } as unknown) as DeckRuntimeServices,
    };
  }

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

  const updateCommandSpy = vi.fn();

  beforeEach(() => {
    getExecutionsForConfigIdsSpy = vi.fn();
    addBuildInfoSpy = vi.spyOn(ExecutionsTransformer, 'addBuildInfo').mockReturnValue(undefined);
    updateCommandSpy.mockClear();
  });

  // Opens the react-select dropdown and clicks the option at the given index.
  // The option order matches the loaded executions order, so index N selects
  // the Nth execution. This replaces direct calls to the private
  // handleExecutionChanged instance method.
  // Note: react-select gives the currently-selected value label role="option" too, so we
  // scope to the menu's `.Select-option` elements (menu items only), which appear in the
  // same order as the loaded executions.
  const getMenuOptions = async (user: ReturnType<typeof setupUser>): Promise<HTMLElement[]> => {
    await user.click(screen.getByRole('combobox'));
    await waitFor(() => expect(document.querySelectorAll('.Select-option').length).toBeGreaterThan(0));
    return Array.from(document.querySelectorAll<HTMLElement>('.Select-option'));
  };

  const selectExecutionByIndex = async (user: ReturnType<typeof setupUser>, index: number) => {
    const options = await getMenuOptions(user);
    await user.click(options[index]);
  };

  const getOptionCount = async (user: ReturnType<typeof setupUser>): Promise<number> => {
    const options = await getMenuOptions(user);
    return options.length;
  };

  describe('Component lifecycle', () => {
    it('displays loading spinner while fetching executions', () => {
      // Promise that never resolves keeps component in loading state
      getExecutionsForConfigIdsSpy.mockReturnValue(new Promise(() => {}));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      const { container } = render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      // Spinner renders a div.load
      expect(container.querySelector('.load')).not.toBeNull();
    });

    it('displays error message on load failure', async () => {
      getExecutionsForConfigIdsSpy.mockReturnValue(Promise.reject(new Error('Load failed')));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      expect(await screen.findByText(/Error loading executions/)).toBeInTheDocument();
    });

    it('displays "No recent executions found" when list is empty', async () => {
      getExecutionsForConfigIdsSpy.mockReturnValue(Promise.resolve([]));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      expect(await screen.findByText('No recent executions found')).toBeInTheDocument();
    });

    it('renders execution dropdown with correct options after load', async () => {
      const user = setupUser();
      const executions = [execution1, execution2, execution3];
      getExecutionsForConfigIdsSpy.mockReturnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      // Dropdown rendered once executions load
      expect(await screen.findByRole('combobox')).toBeInTheDocument();
      // One option per execution, in the loaded order (default selects the first)
      expect(await getOptionCount(user)).toBe(3);
      expect(command.extraFields.parentPipelineId).toBe('exec-1');
    });
  });

  describe('Execution selection preservation', () => {
    it('preserves selection when command object reference changes but trigger.pipeline is unchanged', async () => {
      const user = setupUser();
      const executions = [execution1, execution2, execution3];
      getExecutionsForConfigIdsSpy.mockReturnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      const { rerender } = render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await screen.findByRole('combobox');
      expect(command.extraFields.parentPipelineId).toBe('exec-1');

      // User selects a different execution through the dropdown
      await selectExecutionByIndex(user, 1);
      expect(command.extraFields.parentPipelineId).toBe('exec-2');

      // Simulating Formik behavior: when user types in any form field,
      // Formik creates a NEW command object via spread operator.
      // The object reference changes but trigger.pipeline value stays same.
      const newCommand = {
        ...command,
        parameters: { changeNumber: 'CHG000123' },
      };

      getExecutionsForConfigIdsSpy.mockClear();
      rerender(<PipelineTriggerTemplate command={newCommand} updateCommand={updateCommandSpy} />);

      // Key assertion: API should NOT be called again since pipeline didn't change
      expect(getExecutionsForConfigIdsSpy).not.toHaveBeenCalled();
      expect(newCommand.extraFields.parentPipelineId).toBe('exec-2');
    });

    it('preserves user selection after initial load completes', async () => {
      const user = setupUser();
      const executions = [execution1, execution2, execution3];
      getExecutionsForConfigIdsSpy.mockReturnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await screen.findByRole('combobox');

      await selectExecutionByIndex(user, 2);

      expect(command.extraFields.parentPipelineId).toBe('exec-3');
    });
  });

  describe('Re-initialization behavior', () => {
    it('refetches executions when trigger.pipeline changes', async () => {
      const executions = [execution1, execution2];
      getExecutionsForConfigIdsSpy.mockReturnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      const { rerender } = render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await screen.findByRole('combobox');

      getExecutionsForConfigIdsSpy.mockClear();

      const newTrigger = createPipelineTrigger('different-pipeline-id');
      const newCommand = createCommand(newTrigger);
      rerender(<PipelineTriggerTemplate command={newCommand} updateCommand={updateCommandSpy} />);

      expect(getExecutionsForConfigIdsSpy).toHaveBeenCalledWith(['different-pipeline-id'], { limit: 20 });
    });

    it('defaults to latest execution when parentPipelineId does not match', async () => {
      const executions = [execution1, execution2, execution3];
      getExecutionsForConfigIdsSpy.mockReturnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id', 'non-existent-id');
      const command = createCommand(trigger);

      render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await screen.findByRole('combobox');

      expect(command.extraFields.parentPipelineId).toBe('exec-1');
    });

    it('selects matching execution when parentPipelineId exists in list', async () => {
      const executions = [execution1, execution2, execution3];
      getExecutionsForConfigIdsSpy.mockReturnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id', 'exec-2');
      const command = createCommand(trigger);

      render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await screen.findByRole('combobox');

      expect(command.extraFields.parentPipelineId).toBe('exec-2');
    });
  });

  // Re-run = user clicks "Start execution with same parameters" on an existing execution.
  // In this case, trigger.parentExecution contains the original execution's data,
  // but trigger.pipeline/application may be unset. The component extracts these fields.
  describe('Re-run scenario', () => {
    it('extracts fields from parentExecution', async () => {
      const executions = [execution1, execution2];
      getExecutionsForConfigIdsSpy.mockReturnValue(Promise.resolve(executions));

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

      render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      // The component MUTATES the trigger object to populate these fields
      expect(trigger.application).toBe('parent-app');
      expect(trigger.pipeline).toBe('parent-pipeline-config-id');
      expect(trigger.parentPipelineId).toBe('parent-exec-id');
      expect(getExecutionsForConfigIdsSpy).toHaveBeenCalledWith(['parent-pipeline-config-id'], { limit: 20 });
    });
  });

  describe('User interaction', () => {
    it('updates extraFields when user changes execution selection', async () => {
      const user = setupUser();
      const executions = [execution1, execution2];
      getExecutionsForConfigIdsSpy.mockReturnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await screen.findByRole('combobox');

      expect(command.extraFields.parentPipelineId).toBe('exec-1');
      expect(command.triggerInvalid).toBe(false);

      await selectExecutionByIndex(user, 1);

      expect(command.extraFields.parentPipelineId).toBe('exec-2');
      expect(command.extraFields.parentPipelineApplication).toBe('test-app');
    });

    it('sets triggerInvalid to false after successful execution selection', async () => {
      const executions = [execution1];
      getExecutionsForConfigIdsSpy.mockReturnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);
      command.triggerInvalid = true; // Start with invalid

      render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await screen.findByRole('combobox');

      // After successful load and selection, trigger should be valid
      expect(command.triggerInvalid).toBe(false);
    });

    it('handles multiple rapid selection changes correctly', async () => {
      const user = setupUser();
      const executions = [execution1, execution2, execution3];
      getExecutionsForConfigIdsSpy.mockReturnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await screen.findByRole('combobox');

      // Rapidly change selections
      await selectExecutionByIndex(user, 1); // exec-2
      await selectExecutionByIndex(user, 2); // exec-3
      await selectExecutionByIndex(user, 0); // exec-1

      // Final selection should be the last one
      expect(command.extraFields.parentPipelineId).toBe('exec-1');
    });
  });

  describe('Edge cases', () => {
    it('handles trigger with undefined pipeline gracefully', async () => {
      getExecutionsForConfigIdsSpy.mockReturnValue(Promise.resolve([]));

      const trigger = ({
        enabled: true,
        type: 'pipeline',
        application: 'source-app',
        pipeline: undefined,
        status: ['successful'],
      } as unknown) as IPipelineTrigger;

      const command = createCommand(trigger);

      expect(() => {
        render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);
      }).not.toThrow();

      expect(getExecutionsForConfigIdsSpy).toHaveBeenCalledWith([undefined], { limit: 20 });
    });

    it('handles trigger type change from pipeline to non-pipeline', async () => {
      const executions = [execution1, execution2];
      getExecutionsForConfigIdsSpy.mockReturnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      const { rerender } = render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await screen.findByRole('combobox');

      expect(command.extraFields.parentPipelineId).toBe('exec-1');

      getExecutionsForConfigIdsSpy.mockClear();

      const manualTrigger = { type: 'manual', enabled: true } as any;
      const newCommand = { ...command, trigger: manualTrigger };
      rerender(<PipelineTriggerTemplate command={newCommand} updateCommand={updateCommandSpy} />);

      expect(getExecutionsForConfigIdsSpy).not.toHaveBeenCalled();
    });

    it('handles empty pipeline ID string', async () => {
      getExecutionsForConfigIdsSpy.mockReturnValue(Promise.resolve([]));

      const trigger = createPipelineTrigger('');
      const command = createCommand(trigger);

      render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      expect(getExecutionsForConfigIdsSpy).toHaveBeenCalledWith([''], { limit: 20 });
      expect(await screen.findByText('No recent executions found')).toBeInTheDocument();
    });

    it('handles executions with various status values', async () => {
      const user = setupUser();
      const successExec = createExecution('exec-success', 1, { status: 'SUCCEEDED' });
      const failedExec = createExecution('exec-failed', 2, { status: 'TERMINAL' });
      const runningExec = createExecution('exec-running', 3, { status: 'RUNNING' });
      const canceledExec = createExecution('exec-canceled', 4, { status: 'CANCELED' });

      const executions = [successExec, failedExec, runningExec, canceledExec];
      getExecutionsForConfigIdsSpy.mockReturnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await screen.findByRole('combobox');

      // One option per execution (all statuses are listed), default selects the first
      expect(await getOptionCount(user)).toBe(4);
      expect(command.extraFields.parentPipelineId).toBe('exec-success');
    });
  });

  describe('Configurable execution option limit (SETTINGS.maxPipelineTriggerExecutionOptions)', () => {
    const originalLimit = SETTINGS.maxPipelineTriggerExecutionOptions;

    afterEach(() => {
      SETTINGS.maxPipelineTriggerExecutionOptions = originalLimit;
    });

    it('defaults to 20 when no override is configured', async () => {
      expect(SETTINGS.maxPipelineTriggerExecutionOptions).toBe(20);

      getExecutionsForConfigIdsSpy.mockReturnValue(Promise.resolve([]));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      expect(getExecutionsForConfigIdsSpy).toHaveBeenCalledWith(['source-pipeline-id'], { limit: 20 });
      expect(await screen.findByText('No recent executions found')).toBeInTheDocument();
    });

    it('passes the configured override through to getExecutionsForConfigIds', async () => {
      SETTINGS.maxPipelineTriggerExecutionOptions = 5;
      getExecutionsForConfigIdsSpy.mockReturnValue(Promise.resolve([]));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      expect(getExecutionsForConfigIdsSpy).toHaveBeenCalledWith(['source-pipeline-id'], { limit: 5 });
      expect(await screen.findByText('No recent executions found')).toBeInTheDocument();
    });

    it('renders exactly as many dropdown options as executions returned under a custom limit', async () => {
      const user = setupUser();
      SETTINGS.maxPipelineTriggerExecutionOptions = 2;
      // Simulate the backend honoring the overridden limit by returning only 2 executions,
      // even though 3 exist for this pipeline in these specs (execution1/2/3).
      const executions = [execution1, execution2];
      getExecutionsForConfigIdsSpy.mockReturnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await screen.findByRole('combobox');

      expect(getExecutionsForConfigIdsSpy).toHaveBeenCalledWith(['source-pipeline-id'], { limit: 2 });
      expect(await getOptionCount(user)).toBe(2);

      // Options are listed in the loaded order: exec-1, then exec-2
      await selectExecutionByIndex(user, 0);
      expect(command.extraFields.parentPipelineId).toBe('exec-1');
      await selectExecutionByIndex(user, 1);
      expect(command.extraFields.parentPipelineId).toBe('exec-2');
    });

    it('re-reads the current SETTINGS value on every re-initialization (source pipeline change)', async () => {
      getExecutionsForConfigIdsSpy.mockReturnValue(Promise.resolve([]));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      const { rerender } = render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      expect(getExecutionsForConfigIdsSpy).toHaveBeenCalledWith(['source-pipeline-id'], { limit: 20 });
      expect(await screen.findByText('No recent executions found')).toBeInTheDocument();
      getExecutionsForConfigIdsSpy.mockClear();

      // Operator changes the setting at runtime (e.g. via settings-local.js hot-reload in dev,
      // or simply because a later test/session picked a different value) - the NEXT fetch
      // triggered by switching source pipelines should honor the new value immediately.
      SETTINGS.maxPipelineTriggerExecutionOptions = 7;
      const newTrigger = createPipelineTrigger('different-pipeline-id');
      const newCommand = createCommand(newTrigger);
      rerender(<PipelineTriggerTemplate command={newCommand} updateCommand={updateCommandSpy} />);

      expect(getExecutionsForConfigIdsSpy).toHaveBeenCalledWith(['different-pipeline-id'], { limit: 7 });
      expect(await screen.findByText('No recent executions found')).toBeInTheDocument();
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

      getExecutionsForConfigIdsSpy.mockReturnValueOnce(firstPromise).mockReturnValueOnce(secondPromise);

      const trigger = createPipelineTrigger('pipeline-1');
      const command = createCommand(trigger);

      const { rerender } = render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      // User switches to pipeline-2 before pipeline-1 request finishes
      const newTrigger = createPipelineTrigger('pipeline-2');
      const newCommand = createCommand(newTrigger);
      rerender(<PipelineTriggerTemplate command={newCommand} updateCommand={updateCommandSpy} />);

      // Pipeline-2 response arrives first (as expected)
      const pipeline2Executions = [createExecution('exec-p2-1', 1)];
      resolveSecond(pipeline2Executions);
      await waitFor(() => expect(newCommand.extraFields.parentPipelineId).toBe('exec-p2-1'));

      // Pipeline-1 response arrives late - this is the problem
      const pipeline1Executions = [createExecution('exec-p1-1', 1)];
      resolveFirst(pipeline1Executions);

      // Current behavior: late response overwrites the correct data
      // Expected behavior (when fixed): should still show exec-p2-1
      await waitFor(() => expect(newCommand.extraFields.parentPipelineId).toBe('exec-p1-1'));
    });

    it('maintains loading state until promise resolves', async () => {
      let resolvePromise: (value: IExecution[]) => void = () => {};
      const pendingPromise = new Promise<IExecution[]>((resolve) => {
        resolvePromise = resolve;
      });

      getExecutionsForConfigIdsSpy.mockReturnValue(pendingPromise);

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      const { container } = render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      // Spinner shown while loading; no dropdown yet
      expect(container.querySelector('.load')).not.toBeNull();
      expect(screen.queryByRole('combobox')).toBeNull();

      resolvePromise([execution1]);

      // Once resolved, spinner disappears and the dropdown renders
      expect(await screen.findByRole('combobox')).toBeInTheDocument();
      expect(container.querySelector('.load')).toBeNull();
    });

    it('calls addBuildInfo for each execution after load', async () => {
      const executions = [execution1, execution2, execution3];
      getExecutionsForConfigIdsSpy.mockReturnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await waitFor(() => expect(addBuildInfoSpy).toHaveBeenCalledTimes(3));
      expect(addBuildInfoSpy).toHaveBeenCalledWith(execution1);
      expect(addBuildInfoSpy).toHaveBeenCalledWith(execution2);
      expect(addBuildInfoSpy).toHaveBeenCalledWith(execution3);
    });
  });

  describe('State consistency', () => {
    it('maintains state after multiple prop updates without pipeline change', async () => {
      const user = setupUser();
      const executions = [execution1, execution2, execution3];
      getExecutionsForConfigIdsSpy.mockReturnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      const { rerender } = render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await screen.findByRole('combobox');

      await selectExecutionByIndex(user, 1); // exec-2

      for (let i = 0; i < 5; i++) {
        const updatedCommand = {
          ...command,
          parameters: { changeNumber: `CHG00${i}` },
        };
        rerender(<PipelineTriggerTemplate command={updatedCommand} updateCommand={updateCommandSpy} />);
      }

      // Selection preserved, executions still loaded (dropdown present), no error/empty state
      expect(command.extraFields.parentPipelineId).toBe('exec-2');
      expect(screen.getByRole('combobox')).toBeInTheDocument();
      expect(await getOptionCount(user)).toBe(3);
      expect(screen.queryByText('No recent executions found')).toBeNull();
      expect(screen.queryByText(/Error loading executions/)).toBeNull();
    });

    it('loads new executions when pipeline changes', async () => {
      const user = setupUser();
      const pipeline1Executions = [createExecution('p1-exec-1', 1), createExecution('p1-exec-2', 2)];

      const pipeline2Executions = [
        createExecution('p2-exec-1', 1),
        createExecution('p2-exec-2', 2),
        createExecution('p2-exec-3', 3),
      ];

      getExecutionsForConfigIdsSpy
        .mockReturnValueOnce(Promise.resolve(pipeline1Executions))
        .mockReturnValueOnce(Promise.resolve(pipeline2Executions));

      const trigger = createPipelineTrigger('pipeline-1');
      const command = createCommand(trigger);

      const { rerender } = render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await screen.findByRole('combobox');

      expect(command.extraFields.parentPipelineId).toBe('p1-exec-1');
      expect(await getOptionCount(user)).toBe(2);

      await selectExecutionByIndex(user, 1);
      expect(command.extraFields.parentPipelineId).toBe('p1-exec-2');

      const newTrigger = createPipelineTrigger('pipeline-2');
      const newCommand = createCommand(newTrigger);
      rerender(<PipelineTriggerTemplate command={newCommand} updateCommand={updateCommandSpy} />);

      await waitFor(() => expect(newCommand.extraFields.parentPipelineId).toBe('p2-exec-1'));
      expect(await getOptionCount(user)).toBe(3);
    });

    it('clears extraFields when pipeline changes', async () => {
      const executions = [execution1, execution2];
      getExecutionsForConfigIdsSpy.mockReturnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('pipeline-1');
      const command = createCommand(trigger);

      const { rerender } = render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await screen.findByRole('combobox');

      expect(command.extraFields.parentPipelineId).toBe('exec-1');

      const newTrigger = createPipelineTrigger('pipeline-2');
      const newCommand = createCommand(newTrigger);
      rerender(<PipelineTriggerTemplate command={newCommand} updateCommand={updateCommandSpy} />);

      // initialize() clears extraFields synchronously before the (async) load resolves
      expect(newCommand.extraFields).toEqual({});
    });
  });

  describe('Formik integration', () => {
    it('preserves selection through typical form interaction sequence', async () => {
      const user = setupUser();
      const executions = [execution1, execution2, execution3];
      getExecutionsForConfigIdsSpy.mockReturnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      const { rerender } = render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await screen.findByRole('combobox');
      expect(command.extraFields.parentPipelineId).toBe('exec-1');

      await selectExecutionByIndex(user, 2); // exec-3
      expect(command.extraFields.parentPipelineId).toBe('exec-3');

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

        getExecutionsForConfigIdsSpy.mockClear();
        rerender(<PipelineTriggerTemplate command={newCommand} updateCommand={updateCommandSpy} />);

        expect(getExecutionsForConfigIdsSpy).not.toHaveBeenCalled();
        expect(command.extraFields.parentPipelineId).toBe('exec-3');
      }

      expect(command.extraFields.parentPipelineId).toBe('exec-3');
    });

    it('handles simultaneous parameter and trigger changes correctly', async () => {
      const executions = [execution1, execution2];
      getExecutionsForConfigIdsSpy.mockReturnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('pipeline-1');
      const command = createCommand(trigger);

      const { rerender } = render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await screen.findByRole('combobox');

      getExecutionsForConfigIdsSpy.mockClear();

      const newTrigger = createPipelineTrigger('pipeline-2');
      const newCommand = {
        ...createCommand(newTrigger),
        parameters: { newParam: 'value' },
      };
      rerender(<PipelineTriggerTemplate command={newCommand} updateCommand={updateCommandSpy} />);

      expect(getExecutionsForConfigIdsSpy).toHaveBeenCalledWith(['pipeline-2'], { limit: 20 });
    });
  });

  describe('Rendering', () => {
    it('renders with form-group structure and label', async () => {
      const executions = [execution1];
      getExecutionsForConfigIdsSpy.mockReturnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      const { container } = render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await screen.findByRole('combobox');

      expect(container.querySelector('.form-group')).not.toBeNull();
      const label = screen.getByText('Execution');
      expect(label.tagName).toBe('LABEL');
    });

    it('renders all executions as dropdown options', async () => {
      const user = setupUser();
      const manyExecutions = Array.from({ length: 15 }, (_, i) => createExecution(`exec-${i}`, i));

      getExecutionsForConfigIdsSpy.mockReturnValue(Promise.resolve(manyExecutions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await screen.findByRole('combobox');

      expect(await getOptionCount(user)).toBe(15);
    });

    it('dropdown is not clearable', async () => {
      const executions = [execution1];
      getExecutionsForConfigIdsSpy.mockReturnValue(Promise.resolve(executions));

      const trigger = createPipelineTrigger('source-pipeline-id');
      const command = createCommand(trigger);

      const { container } = render(<PipelineTriggerTemplate command={command} updateCommand={updateCommandSpy} />);

      await screen.findByRole('combobox');

      // react-select adds the 'is-clearable' class only when clearable is true
      expect(container.querySelector('.Select')).not.toBeNull();
      expect(container.querySelector('.Select.is-clearable')).toBeNull();
    });
  });
});
