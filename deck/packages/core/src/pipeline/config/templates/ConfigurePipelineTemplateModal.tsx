import React from 'react';
import { Modal } from 'react-bootstrap';

import {
  buildTemplateConfig,
  initializeTemplateConfiguration,
  mergeTemplatePlan,
  validateTemplateVariable,
} from './PipelineTemplateConfigurationAdapters';
import type { ITemplateConfigurationState, ITemplateInheritance } from './PipelineTemplateConfigurationAdapters';
import type { IPipelineTemplateConfig, IPipelineTemplatePlanError } from './PipelineTemplateReader';
import { PipelineTemplateReader } from './PipelineTemplateReader';
import { TemplatePlanErrors } from './TemplatePlanErrors';
import { Variable } from './Variable';
import type { Application } from '../../../application';
import type { IPipeline, IPipelineTemplateConfigV2 } from '../../../domain';
import type { IVariable } from './inputs/variableInput.service';
import { ModalClose } from '../../../modal';
import type { IModalComponentProps } from '../../../presentation';
import './templateVariableRegistrations';
import { PipelineTemplateV2Service } from './v2/pipelineTemplateV2.service';
import { Spinner } from '../../../widgets';

export interface IConfigurePipelineTemplateModalResult {
  plan: IPipeline;
  config: IPipeline;
}

export interface IConfigurePipelineTemplateModalProps
  extends IModalComponentProps<IConfigurePipelineTemplateModalResult> {
  application: Application;
  executionId?: string;
  isNew: boolean;
  pipelineId: string;
  pipelineTemplateConfig: IPipelineTemplateConfig | IPipelineTemplateConfigV2;
}

interface IConfigurePipelineTemplateModalState {
  configuration?: ITemplateConfigurationState;
  loading: boolean;
  loadingError: boolean;
  planErrors?: IPipelineTemplatePlanError[];
  submissionError: boolean;
  submitting: boolean;
}

export class ConfigurePipelineTemplateModal extends React.Component<
  IConfigurePipelineTemplateModalProps,
  IConfigurePipelineTemplateModalState
> {
  public state: IConfigurePipelineTemplateModalState = {
    loading: true,
    loadingError: false,
    submissionError: false,
    submitting: false,
  };

  private mounted = false;
  private submissionInProgress = false;
  private completed = false;

  public componentDidMount(): void {
    this.mounted = true;
    const { executionId, pipelineId, pipelineTemplateConfig } = this.props;
    const isV2 = PipelineTemplateV2Service.isV2PipelineConfig(pipelineTemplateConfig);
    const source = isV2
      ? (pipelineTemplateConfig as IPipelineTemplateConfigV2).template.reference
      : (pipelineTemplateConfig as IPipelineTemplateConfig).config.pipeline.template.source;

    Promise.resolve(PipelineTemplateReader.getPipelineTemplateFromSourceUrl(source, executionId, pipelineId)).then(
      (template) => {
        if (this.mounted) {
          this.setState({
            configuration: initializeTemplateConfiguration(template, pipelineTemplateConfig),
            loading: false,
          });
        }
      },
      () => {
        if (this.mounted) {
          this.setState({ loading: false, loadingError: true });
        }
      },
    );
  }

  public componentWillUnmount(): void {
    this.mounted = false;
  }

  private handleVariableChange = (newVariable: IVariable): void => {
    const variable = {
      ...newVariable,
      errors: validateTemplateVariable(newVariable, this.state.configuration.isV2),
      hideErrors: false,
    };
    this.setState(({ configuration }) => ({
      configuration: {
        ...configuration,
        variables: configuration.variables.map((current) => (current.name === variable.name ? variable : current)),
      },
    }));
  };

  private toggleInheritance = (key: keyof ITemplateInheritance) => (event: React.ChangeEvent<HTMLInputElement>) => {
    const checked = event.target.checked;
    this.setState(({ configuration }) => ({
      configuration: {
        ...configuration,
        inheritance: { ...configuration.inheritance, [key]: checked },
      },
    }));
  };

  private dismissPlanErrors = (event: React.MouseEvent<HTMLAnchorElement>): void => {
    event.preventDefault();
    this.setState({ planErrors: null });
  };

  private submit = (): void => {
    if (this.submissionInProgress || this.completed) {
      return;
    }
    this.submissionInProgress = true;
    this.setState({ submissionError: false, submitting: true });

    const { application, pipelineId, pipelineTemplateConfig } = this.props;
    const configuration = this.state.configuration;
    let config: IPipelineTemplateConfig | IPipelineTemplateConfigV2;
    let planRequest: PromiseLike<IPipeline>;
    try {
      config = buildTemplateConfig(application.name, pipelineId, pipelineTemplateConfig, configuration);
      planRequest = PipelineTemplateReader.getPipelinePlan(config);
    } catch (_error) {
      this.handleSubmissionFailure();
      return;
    }
    Promise.resolve(planRequest).then(
      (plan) => {
        if (!this.mounted || this.completed) {
          return;
        }
        this.completed = true;
        this.props.closeModal({ plan, config: mergeTemplatePlan(config, plan, configuration) });
      },
      (response) => {
        this.handleSubmissionFailure(response?.data?.errors);
      },
    );
  };

  private handleSubmissionFailure(planErrors?: IPipelineTemplatePlanError[]): void {
    this.submissionInProgress = false;
    if (this.mounted) {
      this.setState({
        planErrors: planErrors?.length ? planErrors : null,
        submissionError: !planErrors?.length,
        submitting: false,
      });
    }
  }

  private renderInheritanceOption(
    label: string,
    key: keyof ITemplateInheritance,
  ): React.ReactElement<HTMLLabelElement> {
    const { inheritance } = this.state.configuration;
    return (
      <label className="checkbox">
        <input type="checkbox" checked={inheritance[key]} onChange={this.toggleInheritance(key)} /> {label}
      </label>
    );
  }

  public render(): React.ReactElement<ConfigurePipelineTemplateModal> {
    const { dismissModal, isNew, pipelineTemplateConfig } = this.props;
    const { configuration, loading, loadingError, planErrors, submissionError, submitting } = this.state;
    const pipelineName = PipelineTemplateV2Service.isV2PipelineConfig(pipelineTemplateConfig)
      ? pipelineTemplateConfig.name
      : (pipelineTemplateConfig as IPipelineTemplateConfig).config.pipeline.name;
    const variables = configuration?.variables || [];
    const noVariables = !!configuration && variables.length === 0;
    const formIsValid = variables.every((variable) => !variable.errors?.length);

    return (
      <>
        <ModalClose dismiss={dismissModal} />
        <Modal.Header>
          <Modal.Title>Define template parameters: {pipelineName}</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {loading && (
            <div className="text-center" style={{ height: 300 }}>
              <Spinner size="small" />
            </div>
          )}
          {loadingError && <div className="alert alert-danger">Could not load pipeline template.</div>}
          {submissionError && (
            <div className="alert alert-danger" data-test-id="template-plan-failure">
              Could not generate pipeline from provided template configuration. Please try again.
            </div>
          )}
          {planErrors && (
            <div className="alert alert-danger">
              <p>Could not generate pipeline from provided template configuration.</p>
              <TemplatePlanErrors errors={planErrors} />
              <p>
                <a data-test-id="template-plan-errors-dismiss" onClick={this.dismissPlanErrors}>
                  [dismiss]
                </a>
              </p>
            </div>
          )}
          {configuration && !planErrors && (
            <>
              {configuration.variableMetadataGroups.map((group) => (
                <section className="pipeline-template-variable-group" data-group={group.name} key={group.name}>
                  <h4>{group.name}</h4>
                  {group.variableMetadata.map((metadata) => (
                    <Variable
                      key={metadata.name}
                      variableMetadata={metadata}
                      variable={variables.find((variable) => variable.name === metadata.name)}
                      onChange={this.handleVariableChange}
                    />
                  ))}
                </section>
              ))}
              {noVariables && <div className="alert alert-info">This template has no variables to configure.</div>}
              <div className="alert alert-info pipeline-template-inheritance">
                <strong>Inherit the following configuration from the template</strong>
                {!configuration.isV2 &&
                  this.renderInheritanceOption('Expected Artifacts', 'inheritTemplateExpectedArtifacts')}
                {configuration.isV2 && this.renderInheritanceOption('Notifications', 'inheritTemplateNotifications')}
                {this.renderInheritanceOption('Parameters', 'inheritTemplateParameters')}
                {this.renderInheritanceOption('Triggers', 'inheritTemplateTriggers')}
              </div>
            </>
          )}
        </Modal.Body>
        {!loading && !loadingError && (
          <Modal.Footer>
            {!isNew && !noVariables && (
              <button className="btn btn-default" onClick={dismissModal} type="button">
                Cancel
              </button>
            )}
            {noVariables && (
              <button className="btn btn-default" disabled={submitting} onClick={this.submit} type="button">
                Dismiss
              </button>
            )}
            {!noVariables && !planErrors && (
              <button
                className="btn btn-primary"
                disabled={!formIsValid || submitting}
                onClick={this.submit}
                type="button"
              >
                {submitting ? 'Configuring...' : 'Configure'}
              </button>
            )}
          </Modal.Footer>
        )}
      </>
    );
  }
}
