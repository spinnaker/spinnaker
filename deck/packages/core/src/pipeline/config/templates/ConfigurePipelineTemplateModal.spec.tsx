import { mount } from 'enzyme';
import React from 'react';
import { act } from 'react-dom/test-utils';

import { ConfigurePipelineTemplateModal } from './ConfigurePipelineTemplateModal';
import { PipelineTemplateReader } from './PipelineTemplateReader';
import type { IPipelineTemplate, IPipelineTemplateConfig } from './PipelineTemplateReader';
import { TemplatePlanErrors } from './TemplatePlanErrors';
import { Variable } from './Variable';
import { ApplicationModelBuilder } from '../../../application/applicationModel.builder';
import type { IPipeline, IPipelineTemplateConfigV2 } from '../../../domain';
import { ModalClose } from '../../../modal';
import { Spinner } from '../../../widgets';

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

  const flush = async () => {
    await Promise.resolve();
    await new Promise((resolve) => setTimeout(resolve, 0));
    await Promise.resolve();
  };

  const deferred = <T,>() => {
    let resolve: (value: T) => void;
    let reject: (reason?: any) => void;
    const promise = new Promise<T>((resolvePromise, rejectPromise) => {
      resolve = resolvePromise;
      reject = rejectPromise;
    });
    return { promise, resolve, reject };
  };

  const mountModal = (pipelineTemplateConfig: IPipelineTemplateConfig | IPipelineTemplateConfigV2, isNew = false) => {
    const closeModal = jasmine.createSpy('closeModal');
    const dismissModal = jasmine.createSpy('dismissModal');
    const wrapper = mount(
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
    return { wrapper, closeModal, dismissModal };
  };

  const findButton = (wrapper: any, label: string) =>
    wrapper
      .find('button')
      .hostNodes()
      .filterWhere((button: any) => button.text() === label)
      .first();

  const findVariable = (wrapper: any, name: string) =>
    wrapper
      .find(Variable)
      .filterWhere((node: any) => node.prop('variableMetadata').name === name)
      .first();

  it('loads by source and renders loading, close, grouped V1 variables, validation, and inheritance controls', async () => {
    const loadRequest = deferred<IPipelineTemplate>();
    spyOn(PipelineTemplateReader, 'getPipelineTemplateFromSourceUrl').and.returnValue(loadRequest.promise);
    const { wrapper, dismissModal } = mountModal(v1Config());

    expect(wrapper.find(Spinner).exists()).toBe(true);
    expect(wrapper.find(ModalClose).exists()).toBe(true);
    wrapper.find(ModalClose).prop('dismiss')();
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
      await flush();
    });
    wrapper.update();

    expect(wrapper.find('.pipeline-template-variable-group').map((group) => group.prop('data-group'))).toEqual([
      'Deploy',
      'Ungrouped',
    ]);
    expect(wrapper.find(Variable).map((variable) => variable.prop('variableMetadata').name)).toEqual([
      'configured',
      'second',
      'required',
    ]);
    expect(findVariable(wrapper, 'configured').prop('variable').value).toBe('current value');
    expect(wrapper.text()).toContain('Expected Artifacts');
    expect(wrapper.text()).toContain('Parameters');
    expect(wrapper.text()).toContain('Triggers');
    expect(wrapper.text()).not.toContain('Notifications');
    expect(findButton(wrapper, 'Configure').prop('disabled')).toBe(true);

    await act(async () => {
      findVariable(wrapper, 'required').prop('onChange')({ name: 'required', type: 'string', value: 'now valid' });
      await flush();
    });
    wrapper.update();

    const updated = findVariable(wrapper, 'required').prop('variable');
    expect(updated.hideErrors).toBe(false);
    expect(updated.errors).toEqual([]);
    expect(findButton(wrapper, 'Configure').prop('disabled')).toBe(false);

    wrapper.unmount();
  });

  it('renders V2 inheritance labels and only offers Cancel for an existing template with variables', async () => {
    spyOn(PipelineTemplateReader, 'getPipelineTemplateFromSourceUrl').and.returnValue(
      Promise.resolve(template([{ name: 'value', type: 'string', defaultValue: 'valid' }])),
    );
    const { wrapper, dismissModal } = mountModal(v2Config());
    await act(flush);
    wrapper.update();

    expect(wrapper.text()).toContain('Notifications');
    expect(wrapper.text()).toContain('Parameters');
    expect(wrapper.text()).toContain('Triggers');
    expect(wrapper.text()).not.toContain('Expected Artifacts');
    const cancel = findButton(wrapper, 'Cancel');
    expect(cancel.exists()).toBe(true);
    cancel.prop('onClick')();
    expect(dismissModal).toHaveBeenCalledTimes(1);

    wrapper.unmount();
  });

  it('omits Cancel for new templates with variables', async () => {
    spyOn(PipelineTemplateReader, 'getPipelineTemplateFromSourceUrl').and.returnValue(
      Promise.resolve(template([{ name: 'value', type: 'string', defaultValue: 'valid' }])),
    );
    const { wrapper } = mountModal(v1Config(), true);
    await act(flush);
    wrapper.update();

    expect(findButton(wrapper, 'Cancel').exists()).toBe(false);

    wrapper.unmount();
  });

  it('renders load failures without losing the close control', async () => {
    spyOn(PipelineTemplateReader, 'getPipelineTemplateFromSourceUrl').and.returnValue(
      Promise.reject(new Error('load')),
    );
    const { wrapper } = mountModal(v1Config());
    await act(flush);
    wrapper.update();

    expect(wrapper.text()).toContain('Could not load pipeline template.');
    expect(wrapper.find(ModalClose).exists()).toBe(true);
    expect(findButton(wrapper, 'Configure').exists()).toBe(false);

    wrapper.unmount();
  });

  it('dismisses plan errors for retry while preserving variable input', async () => {
    spyOn(PipelineTemplateReader, 'getPipelineTemplateFromSourceUrl').and.returnValue(
      Promise.resolve(template([{ name: 'value', type: 'string', defaultValue: 'keep me' }])),
    );
    const getPlan = spyOn(PipelineTemplateReader, 'getPipelinePlan');
    const rejectedPlan = deferred<IPipeline>();
    getPlan.and.returnValue(rejectedPlan.promise);
    const { wrapper } = mountModal(v1Config());
    await act(flush);
    wrapper.update();

    await act(async () => {
      findButton(wrapper, 'Configure').prop('onClick')();
      rejectedPlan.reject({ data: { errors: [{ message: 'bad plan', severity: 'ERROR' }] } });
      await flush();
    });
    wrapper.update();

    expect(wrapper.find(TemplatePlanErrors).prop('errors')).toEqual([
      jasmine.objectContaining({ message: 'bad plan' }),
    ]);
    expect(wrapper.text()).toContain('Could not generate pipeline from provided template configuration.');
    wrapper.find('[data-test-id="template-plan-errors-dismiss"]').prop('onClick')({ preventDefault: () => {} });
    wrapper.update();
    expect(wrapper.find(TemplatePlanErrors).exists()).toBe(false);
    expect(wrapper.find(Variable).prop('variable').value).toBe('keep me');

    getPlan.and.returnValue(Promise.resolve({ stages: [] } as IPipeline));
    await act(async () => {
      findButton(wrapper, 'Configure').prop('onClick')();
      await flush();
    });
    expect(getPlan).toHaveBeenCalledTimes(2);

    wrapper.unmount();
  });

  it('renders an unstructured plan failure and allows retry without losing variable input', async () => {
    spyOn(PipelineTemplateReader, 'getPipelineTemplateFromSourceUrl').and.returnValue(
      Promise.resolve(template([{ name: 'value', type: 'string', defaultValue: 'keep me' }])),
    );
    const firstPlan = deferred<IPipeline>();
    const successfulPlan = { stages: [{ refId: '1', type: 'wait' }] } as IPipeline;
    const getPlan = spyOn(PipelineTemplateReader, 'getPipelinePlan').and.returnValues(
      firstPlan.promise,
      Promise.resolve(successfulPlan),
    );
    const { wrapper, closeModal } = mountModal(v1Config());
    await act(flush);
    wrapper.update();

    await act(async () => {
      findVariable(wrapper, 'value').prop('onChange')({ name: 'value', type: 'string', value: 'edited value' });
      findButton(wrapper, 'Configure').prop('onClick')();
      firstPlan.reject(new Error('plan failed'));
      await flush();
    });
    wrapper.update();

    expect(wrapper.find('[data-test-id="template-plan-failure"]').exists()).toBe(true);
    expect(wrapper.find(TemplatePlanErrors).exists()).toBe(false);
    expect(findButton(wrapper, 'Configuring...').exists()).toBe(false);
    expect(findButton(wrapper, 'Configure').prop('disabled')).toBe(false);
    expect(findVariable(wrapper, 'value').prop('variable').value).toBe('edited value');

    await act(async () => {
      findButton(wrapper, 'Configure').prop('onClick')();
      await flush();
    });

    expect(getPlan).toHaveBeenCalledTimes(2);
    expect(closeModal).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });

  it('validates V2 object variables as JSON before submission', async () => {
    spyOn(PipelineTemplateReader, 'getPipelineTemplateFromSourceUrl').and.returnValue(
      Promise.resolve(template([{ name: 'objectValue', type: 'object', defaultValue: { foo: 'bar' } }])),
    );
    const { wrapper } = mountModal(v2Config());
    await act(flush);
    wrapper.update();

    await act(async () => {
      findVariable(wrapper, 'objectValue').prop('onChange')({
        name: 'objectValue',
        type: 'object',
        value: 'foo: bar',
      });
      await flush();
    });
    wrapper.update();

    expect(findVariable(wrapper, 'objectValue').prop('variable').errors).toEqual([
      { message: 'Value must be valid JSON.' },
    ]);
    expect(findButton(wrapper, 'Configure').prop('disabled')).toBe(true);

    await act(async () => {
      findVariable(wrapper, 'objectValue').prop('onChange')({
        name: 'objectValue',
        type: 'object',
        value: '{"foo":"bar"}',
      });
      await flush();
    });
    wrapper.update();

    expect(findVariable(wrapper, 'objectValue').prop('variable').errors).toEqual([]);
    expect(findButton(wrapper, 'Configure').prop('disabled')).toBe(false);
    wrapper.unmount();
  });

  it('recovers from V2 conversion exceptions without remaining in the submitting state', async () => {
    spyOn(PipelineTemplateReader, 'getPipelineTemplateFromSourceUrl').and.returnValue(
      Promise.resolve(template([{ name: 'objectValue', type: 'object', defaultValue: { foo: 'bar' } }])),
    );
    const plan = { stages: [] } as IPipeline;
    const getPlan = spyOn(PipelineTemplateReader, 'getPipelinePlan').and.returnValue(Promise.resolve(plan));
    const { wrapper, closeModal } = mountModal(v2Config());
    await act(flush);
    wrapper.update();

    const modal = wrapper.find(ConfigurePipelineTemplateModal).instance() as ConfigurePipelineTemplateModal;
    await act(async () => {
      modal.setState(({ configuration }) => ({
        configuration: {
          ...configuration,
          variables: configuration.variables.map((variable) => ({ ...variable, value: 'foo: bar', errors: [] })),
        },
      }));
      await flush();
    });
    wrapper.update();

    await act(async () => {
      expect(() => findButton(wrapper, 'Configure').prop('onClick')()).not.toThrow();
      await flush();
    });
    wrapper.update();

    expect(getPlan).not.toHaveBeenCalled();
    expect(wrapper.find('[data-test-id="template-plan-failure"]').exists()).toBe(true);
    expect(findButton(wrapper, 'Configuring...').exists()).toBe(false);
    expect(findButton(wrapper, 'Configure').prop('disabled')).toBe(false);

    await act(async () => {
      findVariable(wrapper, 'objectValue').prop('onChange')({
        name: 'objectValue',
        type: 'object',
        value: '{"foo":"bar"}',
      });
      await flush();
      findButton(wrapper, 'Configure').prop('onClick')();
      await flush();
    });

    expect(getPlan).toHaveBeenCalledTimes(1);
    expect(closeModal).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });

  it('returns the exact V1 plan and merged config when Dismiss is clicked', async () => {
    spyOn(PipelineTemplateReader, 'getPipelineTemplateFromSourceUrl').and.returnValue(Promise.resolve(template()));
    const plan = { stages: [{ refId: '1', type: 'wait' }] } as IPipeline;
    spyOn(PipelineTemplateReader, 'getPipelinePlan').and.returnValue(Promise.resolve(plan));
    const { wrapper, closeModal } = mountModal(v1Config());
    await act(flush);
    wrapper.update();

    expect(wrapper.text()).toContain('This template has no variables to configure.');
    expect(findButton(wrapper, 'Cancel').exists()).toBe(false);
    await act(async () => {
      findButton(wrapper, 'Dismiss').prop('onClick')();
      await flush();
    });

    expect(PipelineTemplateReader.getPipelinePlan).toHaveBeenCalledTimes(1);
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
    });

    wrapper.unmount();
  });

  it('returns the exact V2 plan and merged config when Dismiss is clicked', async () => {
    spyOn(PipelineTemplateReader, 'getPipelineTemplateFromSourceUrl').and.returnValue(Promise.resolve(template()));
    const plan = {
      stages: [{ refId: '1', type: 'wait' }],
      parameterConfig: [{ name: 'parameter' }],
      notifications: [{ type: 'email' }],
      expectedArtifacts: [{ id: 'artifact' }],
      triggers: [{ type: 'manual' }],
    } as IPipeline;
    spyOn(PipelineTemplateReader, 'getPipelinePlan').and.returnValue(Promise.resolve(plan));
    const { wrapper, closeModal } = mountModal(v2Config());
    await act(flush);
    wrapper.update();

    await act(async () => {
      findButton(wrapper, 'Dismiss').prop('onClick')();
      await flush();
    });

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
    });

    wrapper.unmount();
  });

  it('submits and closes exactly once even when the action is triggered twice', async () => {
    spyOn(PipelineTemplateReader, 'getPipelineTemplateFromSourceUrl').and.returnValue(
      Promise.resolve(template([{ name: 'value', type: 'string', defaultValue: 'valid' }])),
    );
    const planRequest = deferred<IPipeline>();
    spyOn(PipelineTemplateReader, 'getPipelinePlan').and.returnValue(planRequest.promise);
    const { wrapper, closeModal } = mountModal(v1Config());
    await act(flush);
    wrapper.update();

    const submit = findButton(wrapper, 'Configure');
    submit.prop('onClick')();
    submit.prop('onClick')();
    expect(PipelineTemplateReader.getPipelinePlan).toHaveBeenCalledTimes(1);

    await act(async () => {
      planRequest.resolve({ stages: [] } as IPipeline);
      await flush();
    });
    expect(closeModal).toHaveBeenCalledTimes(1);

    wrapper.unmount();
  });

  it('does not update state when loading finishes after unmount', async () => {
    const loadRequest = deferred<IPipelineTemplate>();
    spyOn(PipelineTemplateReader, 'getPipelineTemplateFromSourceUrl').and.returnValue(loadRequest.promise);
    const consoleError = spyOn(console, 'error');
    const { wrapper } = mountModal(v1Config());
    wrapper.unmount();

    await act(async () => {
      loadRequest.resolve(template());
      await flush();
    });

    expect(consoleError).not.toHaveBeenCalled();
  });
});
