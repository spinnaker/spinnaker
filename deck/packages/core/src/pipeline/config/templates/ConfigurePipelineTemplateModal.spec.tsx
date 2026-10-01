import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { setupUser } from '../../../utils/testUtils/userEvent';
import React from 'react';

import * as configurationAdapters from './PipelineTemplateConfigurationAdapters';
import { ConfigurePipelineTemplateModal } from './ConfigurePipelineTemplateModal';
import { PipelineTemplateReader } from './PipelineTemplateReader';
import type { IPipelineTemplate, IPipelineTemplateConfig } from './PipelineTemplateReader';
import { ApplicationModelBuilder } from '../../../application/applicationModel.builder';
import type { IPipeline, IPipelineTemplateConfigV2 } from '../../../domain';

describe('ConfigurePipelineTemplateModal', () => {
  const application = ApplicationModelBuilder.createApplicationForTests('app');

  const template = (variables: any[] = []): IPipelineTemplate =>
    ({
      id: 'template-id',
      metadata: { description: '', name: 'Template', owner: 'owner@example.com' },
      protect: false,
      schema: '1',
      source: 'spinnaker://template-id',
      stages: [],
      variables,
    } as IPipelineTemplate);

  const v1Config = (): IPipelineTemplateConfig =>
    ({
      application: 'app',
      id: 'pipeline-id',
      name: 'Pipeline',
      type: 'templatedPipeline',
      config: {
        schema: '1',
        pipeline: {
          application: 'app',
          name: 'Pipeline',
          pipelineConfigId: 'pipeline-id',
          template: { source: 'spinnaker://template-id' },
          variables: { configured: 'current value' },
        },
      },
    } as IPipelineTemplateConfig);

  const v2Config = (): IPipelineTemplateConfigV2 =>
    ({
      application: 'app',
      id: 'pipeline-id',
      name: 'Pipeline',
      schema: 'v2',
      template: {
        artifactAccount: 'front50ArtifactCredentials',
        reference: 'spinnaker://template-id',
        type: 'front50/pipelineTemplate',
      },
      type: 'templatedPipeline',
      variables: {},
    } as IPipelineTemplateConfigV2);

  const deferred = <T,>() => {
    let resolve: (value: T) => void;
    let reject: (reason?: any) => void;
    const promise = new Promise<T>((resolvePromise, rejectPromise) => {
      resolve = resolvePromise;
      reject = rejectPromise;
    });
    return { promise, resolve, reject };
  };

  const renderModal = (pipelineTemplateConfig: IPipelineTemplateConfig | IPipelineTemplateConfigV2, isNew = false) => {
    const closeModal = vi.fn();
    const dismissModal = vi.fn();
    const result = render(
      <ConfigurePipelineTemplateModal
        application={application}
        executionId="execution-id"
        isNew={isNew}
        pipelineId="pipeline-id"
        pipelineTemplateConfig={pipelineTemplateConfig}
        closeModal={closeModal}
        dismissModal={dismissModal}
      />,
    );
    return { ...result, closeModal, dismissModal };
  };

  const getVariableInput = (name: string): HTMLInputElement | HTMLTextAreaElement =>
    screen
      .getByText(name, { selector: 'code' })
      .closest('.pipeline-template-variable')
      .querySelector('input, textarea');

  it('loads by source and renders loading, close, grouped V1 variables, validation, and inheritance controls', async () => {
    const user = setupUser();
    const loadRequest = deferred<IPipelineTemplate>();
    vi.spyOn(PipelineTemplateReader, 'getPipelineTemplateFromSourceUrl').mockReturnValue(loadRequest.promise);
    const { container, dismissModal } = renderModal(v1Config());

    expect(screen.queryByRole('button', { name: 'Configure' })).not.toBeInTheDocument();
    await user.click(container.querySelector('.modal-close button'));
    expect(dismissModal).toHaveBeenCalledTimes(1);
    expect(PipelineTemplateReader.getPipelineTemplateFromSourceUrl).toHaveBeenCalledWith(
      'spinnaker://template-id',
      'execution-id',
      'pipeline-id',
    );

    await act(async () => {
      loadRequest.resolve(
        template([
          { name: 'configured', type: 'string', group: 'Deploy' },
          { name: 'required', type: 'string' },
          { name: 'second', type: 'string', group: 'Deploy', defaultValue: 'second value' },
        ]),
      );
      await loadRequest.promise;
    });

    expect(
      Array.from(container.querySelectorAll('.pipeline-template-variable-group')).map((group) =>
        group.getAttribute('data-group'),
      ),
    ).toEqual(['Deploy', 'Ungrouped']);
    expect(
      Array.from(container.querySelectorAll('.pipeline-template-variable code')).map((code) => code.textContent),
    ).toEqual(['configured', 'second', 'required']);
    expect(getVariableInput('configured')).toHaveValue('current value');
    expect(screen.getByText('Expected Artifacts')).toBeInTheDocument();
    expect(screen.getByText('Parameters')).toBeInTheDocument();
    expect(screen.getByText('Triggers')).toBeInTheDocument();
    expect(screen.queryByText('Notifications')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Configure' })).toBeDisabled();

    fireEvent.change(getVariableInput('required'), { target: { value: 'now valid' } });

    expect(screen.queryByText(/required/i, { selector: '.error-message' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Configure' })).toBeEnabled();
  });

  it('renders V2 inheritance labels and only offers Cancel for an existing template with variables', async () => {
    const user = setupUser();
    vi.spyOn(PipelineTemplateReader, 'getPipelineTemplateFromSourceUrl').mockResolvedValue(
      template([{ name: 'value', type: 'string', defaultValue: 'valid' }]),
    );
    const { dismissModal } = renderModal(v2Config());

    await screen.findByRole('button', { name: 'Configure' });
    expect(screen.getByText('Notifications')).toBeInTheDocument();
    expect(screen.getByText('Parameters')).toBeInTheDocument();
    expect(screen.getByText('Triggers')).toBeInTheDocument();
    expect(screen.queryByText('Expected Artifacts')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(dismissModal).toHaveBeenCalledTimes(1);
  });

  it('omits Cancel for new templates with variables', async () => {
    vi.spyOn(PipelineTemplateReader, 'getPipelineTemplateFromSourceUrl').mockResolvedValue(
      template([{ name: 'value', type: 'string', defaultValue: 'valid' }]),
    );
    renderModal(v1Config(), true);

    await screen.findByRole('button', { name: 'Configure' });
    expect(screen.queryByRole('button', { name: 'Cancel' })).not.toBeInTheDocument();
  });

  it('renders load failures without losing the close control', async () => {
    vi.spyOn(PipelineTemplateReader, 'getPipelineTemplateFromSourceUrl').mockRejectedValue(new Error('load'));
    const { container } = renderModal(v1Config());

    expect(await screen.findByText('Could not load pipeline template.')).toBeVisible();
    expect(container.querySelector('.modal-close button')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Configure' })).not.toBeInTheDocument();
  });

  it('dismisses plan errors for retry while preserving variable input', async () => {
    const user = setupUser();
    vi.spyOn(PipelineTemplateReader, 'getPipelineTemplateFromSourceUrl').mockResolvedValue(
      template([{ name: 'value', type: 'string', defaultValue: 'keep me' }]),
    );
    const rejectedPlan = deferred<IPipeline>();
    const getPlan = vi.spyOn(PipelineTemplateReader, 'getPipelinePlan').mockReturnValue(rejectedPlan.promise);
    renderModal(v1Config());

    await user.click(await screen.findByRole('button', { name: 'Configure' }));
    await act(async () => {
      rejectedPlan.reject({ data: { errors: [{ message: 'bad plan', severity: 'ERROR' }] } });
      await rejectedPlan.promise.catch(() => undefined);
    });

    expect(screen.getByText((_content, element) => element.textContent === 'Message: bad plan')).toBeVisible();
    expect(screen.getByText('Could not generate pipeline from provided template configuration.')).toBeVisible();
    await user.click(screen.getByText('[dismiss]'));
    expect(
      screen.queryByText((_content, element) => element.textContent === 'Message: bad plan'),
    ).not.toBeInTheDocument();
    expect(getVariableInput('value')).toHaveValue('keep me');

    getPlan.mockResolvedValue({ stages: [] } as IPipeline);
    await user.click(screen.getByRole('button', { name: 'Configure' }));
    expect(getPlan).toHaveBeenCalledTimes(2);
  });

  it('renders an unstructured plan failure and allows retry without losing variable input', async () => {
    const user = setupUser();
    vi.spyOn(PipelineTemplateReader, 'getPipelineTemplateFromSourceUrl').mockResolvedValue(
      template([{ name: 'value', type: 'string', defaultValue: 'keep me' }]),
    );
    const firstPlan = deferred<IPipeline>();
    const successfulPlan = { stages: [{ refId: '1', type: 'wait' }] } as IPipeline;
    const getPlan = vi
      .spyOn(PipelineTemplateReader, 'getPipelinePlan')
      .mockReturnValueOnce(firstPlan.promise)
      .mockResolvedValueOnce(successfulPlan);
    const { closeModal } = renderModal(v1Config());

    const input = await waitFor(() => getVariableInput('value'));
    fireEvent.change(input, { target: { value: 'edited value' } });
    await user.click(screen.getByRole('button', { name: 'Configure' }));
    await act(async () => {
      firstPlan.reject(new Error('plan failed'));
      await firstPlan.promise.catch(() => undefined);
    });

    expect(
      screen.getByText(/Could not generate pipeline from provided template configuration\. Please try again\./),
    ).toBeVisible();
    expect(screen.queryByText('Configuring...')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Configure' })).toBeEnabled();
    expect(getVariableInput('value')).toHaveValue('edited value');

    await user.click(screen.getByRole('button', { name: 'Configure' }));
    await waitFor(() => expect(closeModal).toHaveBeenCalledTimes(1));
    expect(getPlan).toHaveBeenCalledTimes(2);
  });

  it('validates V2 object variables as JSON before submission', async () => {
    vi.spyOn(PipelineTemplateReader, 'getPipelineTemplateFromSourceUrl').mockResolvedValue(
      template([{ name: 'objectValue', type: 'object', defaultValue: { foo: 'bar' } }]),
    );
    renderModal(v2Config());

    const input = await waitFor(() => getVariableInput('objectValue'));
    fireEvent.change(input, { target: { value: 'foo: bar' } });
    expect(screen.getByText('Value must be valid JSON.')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Configure' })).toBeDisabled();

    fireEvent.change(input, { target: { value: '{"foo":"bar"}' } });
    expect(screen.queryByText('Value must be valid JSON.')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Configure' })).toBeEnabled();
  });

  it('recovers from V2 conversion exceptions without remaining in the submitting state', async () => {
    const user = setupUser();
    vi.spyOn(PipelineTemplateReader, 'getPipelineTemplateFromSourceUrl').mockResolvedValue(
      template([{ name: 'objectValue', type: 'object', defaultValue: { foo: 'bar' } }]),
    );
    const plan = { stages: [] } as IPipeline;
    const getPlan = vi.spyOn(PipelineTemplateReader, 'getPipelinePlan').mockResolvedValue(plan);
    const buildConfig = vi.spyOn(configurationAdapters, 'buildTemplateConfig').mockImplementationOnce(() => {
      throw new Error('conversion failed');
    });
    const { closeModal } = renderModal(v2Config());

    await user.click(await screen.findByRole('button', { name: 'Configure' }));
    expect(getPlan).not.toHaveBeenCalled();
    expect(
      screen.getByText(/Could not generate pipeline from provided template configuration\. Please try again\./),
    ).toBeVisible();
    expect(screen.queryByText('Configuring...')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Configure' })).toBeEnabled();

    buildConfig.mockRestore();
    await user.click(screen.getByRole('button', { name: 'Configure' }));
    await waitFor(() => expect(closeModal).toHaveBeenCalledTimes(1));
    expect(getPlan).toHaveBeenCalledTimes(1);
  });

  it('returns the exact V1 plan and merged config when Dismiss is clicked', async () => {
    const user = setupUser();
    vi.spyOn(PipelineTemplateReader, 'getPipelineTemplateFromSourceUrl').mockResolvedValue(template());
    const plan = { stages: [{ refId: '1', type: 'wait' }] } as IPipeline;
    vi.spyOn(PipelineTemplateReader, 'getPipelinePlan').mockResolvedValue(plan);
    const { closeModal } = renderModal(v1Config());

    expect(await screen.findByText('This template has no variables to configure.')).toBeVisible();
    expect(screen.queryByRole('button', { name: 'Cancel' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Dismiss' }));

    await waitFor(() =>
      expect(closeModal).toHaveBeenCalledWith({
        plan,
        config: {
          application: 'app',
          id: 'pipeline-id',
          name: 'Pipeline',
          type: 'templatedPipeline',
          config: {
            schema: '1',
            pipeline: {
              application: 'app',
              name: 'Pipeline',
              pipelineConfigId: 'pipeline-id',
              template: { source: 'spinnaker://template-id' },
              variables: {},
            },
            configuration: { inherit: ['parameters', 'expectedArtifacts', 'triggers'] },
          },
        },
      }),
    );
  });

  it('returns the exact V2 plan and merged config when Dismiss is clicked', async () => {
    const user = setupUser();
    vi.spyOn(PipelineTemplateReader, 'getPipelineTemplateFromSourceUrl').mockResolvedValue(template());
    const plan = {
      stages: [{ refId: '1', type: 'wait' }],
      parameterConfig: [{ name: 'parameter' }],
      notifications: [{ type: 'email' }],
      expectedArtifacts: [{ id: 'artifact' }],
      triggers: [{ type: 'manual' }],
    } as IPipeline;
    vi.spyOn(PipelineTemplateReader, 'getPipelinePlan').mockResolvedValue(plan);
    const { closeModal } = renderModal(v2Config());

    await user.click(await screen.findByRole('button', { name: 'Dismiss' }));

    await waitFor(() =>
      expect(closeModal).toHaveBeenCalledWith({
        plan,
        config: {
          application: 'app',
          id: 'pipeline-id',
          name: 'Pipeline',
          schema: 'v2',
          template: {
            artifactAccount: 'front50ArtifactCredentials',
            reference: 'spinnaker://template-id',
            type: 'front50/pipelineTemplate',
          },
          type: 'templatedPipeline',
          variables: {},
          exclude: [],
          parameterConfig: plan.parameterConfig,
          notifications: plan.notifications,
          expectedArtifacts: plan.expectedArtifacts,
          triggers: plan.triggers,
        },
      }),
    );
  });

  it('submits and closes exactly once even when the action is triggered twice', async () => {
    vi.spyOn(PipelineTemplateReader, 'getPipelineTemplateFromSourceUrl').mockResolvedValue(
      template([{ name: 'value', type: 'string', defaultValue: 'valid' }]),
    );
    const planRequest = deferred<IPipeline>();
    vi.spyOn(PipelineTemplateReader, 'getPipelinePlan').mockReturnValue(planRequest.promise);
    const { closeModal } = renderModal(v1Config());

    const submit = await screen.findByRole('button', { name: 'Configure' });
    fireEvent.click(submit);
    fireEvent.click(submit);
    expect(PipelineTemplateReader.getPipelinePlan).toHaveBeenCalledTimes(1);

    await act(async () => {
      planRequest.resolve({ stages: [] } as IPipeline);
      await planRequest.promise;
    });
    expect(closeModal).toHaveBeenCalledTimes(1);
  });

  it('does not update state when loading finishes after unmount', async () => {
    const loadRequest = deferred<IPipelineTemplate>();
    vi.spyOn(PipelineTemplateReader, 'getPipelineTemplateFromSourceUrl').mockReturnValue(loadRequest.promise);
    const consoleError = vi.spyOn(console, 'error').mockReturnValue(undefined);
    const { unmount } = renderModal(v1Config());
    unmount();

    await act(async () => {
      loadRequest.resolve(template());
      await loadRequest.promise;
    });

    expect(consoleError).not.toHaveBeenCalled();
  });
});
