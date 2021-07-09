import { UISref } from '@uirouter/react';
import { IHttpPromiseCallbackArg } from 'angular';
import { cloneDeep, get, uniqBy } from 'lodash';
import { Debounce } from 'lodash-decorators';
import { $log } from 'ngimport';
import React from 'react';
import { Button, Modal } from 'react-bootstrap';
import Select, { Option } from 'react-select';

import { ManagedTemplateSelector } from './ManagedTemplateSelector';
import { TemplateDescription } from './TemplateDescription';
import { Application } from '../../application/application.model';
import { PipelineConfigService } from '../config/services/PipelineConfigService';
import { SETTINGS } from '../../config/settings';
import {
  IPipelineTemplate,
  IPipelineTemplateConfig,
  PipelineTemplateReader,
} from '../config/templates/PipelineTemplateReader';
import { PipelineTemplateV2Service } from '../config/templates/v2/pipelineTemplateV2.service';
import { IPipeline } from '../../domain/IPipeline';
import { IPipelineTemplateV2 } from '../../domain/IPipelineTemplateV2';
import { SubmitButton } from '../../modal/buttons/SubmitButton';
import { Overridable } from '../../overrideRegistry';
import { Spinner } from '../../widgets/spinners/Spinner';

import './createPipelineModal.less';

export interface ICreatePipelineModalState {
  submitting: boolean;
  saveError: boolean;
  saveErrorMessage: string;
  loading: boolean;
  loadError: boolean;
  loadErrorMessage: string;
  command: ICreatePipelineCommand;
  existingNames: string[];
  configs: Array<Partial<IPipeline>>;
  configOptions: Option[];
  templates: IPipelineTemplate[];
  useTemplate: boolean;
  useManagedTemplate: boolean;
  loadingTemplateFromSource: boolean;
  loadingTemplateFromSourceError: boolean;
  templateSourceUrl: string;
  inheritTemplateParameters: boolean;
  inheritTemplateExpectedArtifacts: boolean;
  inheritTemplateTriggers: boolean;
}

export interface ICreatePipelineCommand {
  strategy: boolean;
  name: string;
  config: Partial<IPipeline>;
  template: IPipelineTemplate;
}

export interface ICreatePipelineModalProps {
  application: Application;
  pipelineSavedCallback: (pipelineId: string) => void;
  show: boolean;
  showCallback: (show: boolean) => void;
  preselectedTemplate?: IPipelineTemplateV2;
}

@Overridable('core.pipeline.CreatePipelineModal')
export class CreatePipelineModal extends React.Component<ICreatePipelineModalProps, ICreatePipelineModalState> {
  constructor(props: ICreatePipelineModalProps) {
    super(props);
    this.state = this.getDefaultState();
  }

  public static defaultProps: Partial<ICreatePipelineModalProps> = {
    preselectedTemplate: null,
  };

  public componentDidUpdate(prevProps: ICreatePipelineModalProps): void {
    if (!prevProps.show && this.props.show && !this.state.loading) {
      this.loadPipelineTemplates();
    }
  }

  private getDefaultConfig(): Partial<IPipeline> {
    return {
      name: 'None',
      stages: [],
      triggers: [],
      application: this.props.application.name,
      limitConcurrent: true,
      keepWaitingPipelines: false,
      spelEvaluator: 'v4',
    };
  }

  private getDefaultState(): ICreatePipelineModalState {
    const defaultConfig = this.getDefaultConfig();
    const { application } = this.props;
    const configs: Array<Partial<IPipeline>> = [defaultConfig].concat(get(application, 'pipelineConfigs.data', []));
    const configOptions: Option[] = configs.map((config) => ({ value: config.name, label: config.name }));
    const existingNames: string[] = [defaultConfig]
      .concat(get(application, 'pipelineConfigs.data', []))
      .concat(get(application, 'strategyConfigs.data', []))
      .map((config) => config.name);

    return {
      submitting: false,
      saveError: false,
      saveErrorMessage: null,
      loading: false,
      loadError: false,
      loadErrorMessage: null,
      configs,
      configOptions,
      templates: [],
      existingNames,
      command: { strategy: false, name: '', config: defaultConfig, template: null },
      useTemplate: false,
      useManagedTemplate: true,
      loadingTemplateFromSource: false,
      loadingTemplateFromSourceError: false,
      templateSourceUrl: '',
      inheritTemplateParameters: true,
      inheritTemplateExpectedArtifacts: true,
      inheritTemplateTriggers: true,
    };
  }

  public submit = (): void => {
    const command = cloneDeep(this.state.command);
    const pipelineConfig: Partial<IPipelineTemplateConfig> = command.strategy
      ? this.getDefaultConfig()
      : command.config;

    pipelineConfig.name = command.name;
    pipelineConfig.index = this.props.application.getDataSource('pipelineConfigs').data.length;
    delete pipelineConfig.id;

    if (command.strategy) {
      pipelineConfig.strategy = true;
      pipelineConfig.limitConcurrent = false;
    }
    if (pipelineConfig.type === 'templatedPipeline') {
      delete pipelineConfig.config.pipeline.pipelineConfigId;
      pipelineConfig.config.pipeline.name = command.name;
    }

    this.setState({ submitting: true });
    PipelineConfigService.savePipeline(pipelineConfig as IPipeline).then(
      () => this.onSaveSuccess(pipelineConfig),
      this.onSaveFailure,
    );
  };

  private submitPipelineTemplateConfig = (): void => {
    const { application, preselectedTemplate } = this.props;
    const { command } = this.state;

    const pipelineConfig: Partial<IPipeline> = {
      name: command.name,
      application: application.name,
      type: 'templatedPipeline',
      limitConcurrent: true,
      keepWaitingPipelines: false,
      triggers: [],
    };

    const config = {
      ...pipelineConfig,
      ...(preselectedTemplate
        ? PipelineTemplateV2Service.getPipelineTemplateConfigV2(
            PipelineTemplateV2Service.getTemplateVersion(preselectedTemplate),
          )
        : PipelineTemplateReader.getPipelineTemplateConfig({
            name: command.name,
            application: application.name,
            source: command.template.selfLink,
          })),
    };

    this.setState({ submitting: true });
    PipelineConfigService.savePipeline(config as IPipeline).then(() => this.onSaveSuccess(config), this.onSaveFailure);
  };

  private onSaveSuccess(config: Partial<IPipeline>): void {
    const application = this.props.application;
    application.pipelineConfigs.refresh(true).then(() => {
      const configs: IPipeline[] = config.strategy
        ? application.strategyConfigs.data
        : application.pipelineConfigs.data;
      const newPipeline = configs.find((_config) => _config.name === config.name);

      if (!newPipeline) {
        $log.warn('Could not find new pipeline after save succeeded.');
        this.setState({
          saveError: true,
          saveErrorMessage: 'Sorry, there was an error retrieving your new pipeline. Please refresh the browser.',
          submitting: false,
        });
      } else {
        newPipeline.isNew = true;
        this.setState(this.getDefaultState());
        this.props.pipelineSavedCallback(newPipeline.id);
      }
    });
  }

  private onSaveFailure = (response: IHttpPromiseCallbackArg<{ message: string }>): void => {
    $log.warn(response);
    this.setState({
      submitting: false,
      saveError: true,
      saveErrorMessage: (response && response.data && response.data.message) || 'No message provided',
    });
  };

  public close = (evt?: React.MouseEvent<any>): void => {
    evt && evt.stopPropagation();
    this.setState(this.getDefaultState());
    this.props.showCallback(false);
  };

  private handleTypeChange = (option: Option<boolean>): void => {
    const strategy = option.value;
    this.setState({ command: { ...this.state.command, strategy } });
  };

  private handleNameChange = (e: React.ChangeEvent<HTMLInputElement>): void => {
    this.setState({ command: { ...this.state.command, name: e.target.value } });
  };

  private handleConfigChange = (option: Option): void => {
    const config = this.state.configs.find((t) => t.name === option.value);
    this.setState({ command: { ...this.state.command, config } });
  };

  private handleSaveErrorDismiss = (): void => {
    this.setState({ saveError: false });
  };

  private handleLoadErrorDismiss = (): void => {
    this.setState({ loadError: false });
  };

  private handleTemplateSelection = (template: IPipelineTemplate): void => {
    this.setState({ command: { ...this.state.command, template } });
  };

  private handleUseTemplateSelection(useTemplate: boolean): () => void {
    return () => this.setState({ useTemplate });
  }

  private handleUseManagedTemplateSelection(useManagedTemplate: boolean): () => void {
    return () => {
      this.setState({
        useManagedTemplate,
        templateSourceUrl: '',
        loadingTemplateFromSourceError: false,
        command: { ...this.state.command, template: null },
      });
    };
  }

  public handleSourceUrlChange = (e: React.ChangeEvent<HTMLInputElement>): void => {
    const templateSourceUrl = e.target.value;
    this.setState({ templateSourceUrl });
    this.loadPipelineTemplateFromSource(templateSourceUrl);
  };

  private configOptionRenderer = (option: Option) => {
    const config = this.state.configs.find((t) => t.name === option.value);
    return (
      <div>
        <h5>{config.name}</h5>
        {config.stages.length > 0 && (
          <div className="small">
            <b>Stages: </b>
            <ul>
              {config.stages.map((stage) => (
                <li key={stage.refId}>{stage.name || stage.type}</li>
              ))}
            </ul>
          </div>
        )}
      </div>
    );
  };

  public validateNameCharacters(): boolean {
    return /^[^\\^/?%#]*$/.test(this.state.command.name); // Verify name does not include: \, ^, ?, %, #
  }

  public validateNameIsUnique(): boolean {
    return this.state.existingNames.every((name) => name !== this.state.command.name.trim());
  }

  public loadPipelineTemplates(): void {
    if (SETTINGS.feature.pipelineTemplates) {
      this.setState({ loading: true });
      PipelineTemplateReader.getPipelineTemplatesByScopes([this.props.application.name, 'global'])
        .then((templates) => {
          templates = uniqBy(templates, 'id').filter(({ schema }) => schema !== 'v2');
          this.setState({ templates, loading: false });
        })
        .catch((response: IHttpPromiseCallbackArg<{ message: string }>) => {
          this.setState({
            loadError: true,
            loadErrorMessage: (response && response.data && response.data.message) || 'No message provided',
            loading: false,
          });
        })
        .finally(() => {
          if (!this.state.templates.length) {
            this.setState({ useManagedTemplate: false });
          }
        });
    }
  }

  @Debounce(200)
  private loadPipelineTemplateFromSource(sourceUrl: string): void {
    if (sourceUrl) {
      this.setState({ loadingTemplateFromSource: true, loadingTemplateFromSourceError: false });
      PipelineTemplateReader.getPipelineTemplateFromSourceUrl(sourceUrl)
        .then((template) => (this.state.command.template = template))
        .catch(() => this.setState({ loadingTemplateFromSourceError: true }))
        .finally(() => this.setState({ loadingTemplateFromSource: false }));
    }
  }

  // Prevents the form from reloading the page if the user hits enter on an input.
  private handleFormSubmit = (e: React.FormEvent<HTMLFormElement>) => e.preventDefault();

  public render() {
    const { preselectedTemplate } = this.props;
    const hasSelectedATemplate = this.state.useTemplate || preselectedTemplate;
    const nameHasError = !this.validateNameCharacters();
    const nameIsNotUnique = !this.validateNameIsUnique();
    const formValid =
      !nameHasError &&
      !nameIsNotUnique &&
      this.state.command.name.length > 0 &&
      (!this.state.useTemplate || !!this.state.command.template);

    return (
      <Modal
        show={this.props.show}
        onHide={this.close}
        className="create-pipeline-modal-overflow-visible"
        backdrop="static"
      >
        <Modal.Header closeButton={true}>
          <Modal.Title>Create New {this.state.command.strategy ? 'Strategy' : 'Pipeline'}</Modal.Title>
        </Modal.Header>
        {this.state.loading && (
          <Modal.Body style={{ height: '200px' }}>
            <Spinner size="medium" />
          </Modal.Body>
        )}
        {!this.state.loading && (
          <Modal.Body>
            {this.state.loadError && (
              <div className="alert alert-danger">
                <p>Could not load pipeline templates.</p>
                <p>
                  <strong>Reason: </strong> {this.state.loadErrorMessage}
                </p>
                <p>
                  <a onClick={this.handleLoadErrorDismiss}>[dismiss]</a>
                </p>
              </div>
            )}
            {this.state.saveError && (
              <div className="alert alert-danger">
                <p>Could not save pipeline.</p>
                <p>
                  <strong>Reason: </strong> {this.state.saveErrorMessage}
                </p>
                <p>
                  <a onClick={this.handleSaveErrorDismiss}>[dismiss]</a>
                </p>
              </div>
            )}
            {!(this.state.saveError || this.state.loadError) && (
              <form role="form" name="form" className="clearfix" onSubmit={this.handleFormSubmit}>
                {!preselectedTemplate && (
                  <div className="form-group clearfix">
                    <div className="col-md-3 sm-label-right">
                      <b>Type</b>
                    </div>
                    <div className="col-md-7">
                      <Select
                        options={[
                          { label: 'Pipeline', value: false },
                          { label: 'Strategy', value: true },
                        ]}
                        clearable={false}
                        value={this.state.command.strategy ? { label: 'Strategy' } : { label: 'Pipeline' }}
                        onChange={this.handleTypeChange}
                      />
                    </div>
                  </div>
                )}
                <div className="form-group clearfix">
                  <div className="col-md-3 sm-label-right">
                    <b>{this.state.command.strategy ? 'Strategy' : 'Pipeline'} Name</b>
                  </div>
                  <div className="col-md-7">
                    <input
                      type="text"
                      className="form-control"
                      value={this.state.command.name}
                      onChange={this.handleNameChange}
                      required={true}
                      autoFocus={true}
                    />
                  </div>
                </div>
                {nameHasError && (
                  <div className="form-group row slide-in">
                    <div className="col-sm-9 col-sm-offset-3 error-message">
                      <div>
                        {this.state.command.strategy ? 'Strategy' : 'Pipeline'} name cannot contain any of the following
                        characters:
                      </div>
                      <code>/ \ ? % #</code>
                    </div>
                  </div>
                )}
                {nameIsNotUnique && (
                  <div className="form-group row slide-in">
                    <div className="col-sm-9 col-sm-offset-3 error-message">
                      <span>
                        There is already a {this.state.command.strategy ? 'strategy' : 'pipeline'} with that name.
                      </span>
                    </div>
                  </div>
                )}
                {SETTINGS.feature.pipelineTemplates && !preselectedTemplate && !this.state.command.strategy && (
                  <div className="form-group clearfix">
                    <div className="col-md-3 sm-label-right">
                      <strong>Create From</strong>
                    </div>
                    <div className="col-md-7">
                      <label className="radio-inline">
                        <input
                          type="radio"
                          checked={!this.state.useTemplate}
                          onChange={this.handleUseTemplateSelection(false)}
                        />
                        Pipeline
                      </label>
                      <label className="radio-inline">
                        <input
                          type="radio"
                          checked={this.state.useTemplate}
                          onChange={this.handleUseTemplateSelection(true)}
                        />
                        Template
                      </label>
                    </div>
                  </div>
                )}
                {this.state.configs.length > 1 && !this.state.command.strategy && !this.state.useTemplate && (
                  <div>
                    {SETTINGS.feature.pipelineTemplates && <hr />}
                    <div className="form-group clearfix">
                      <div className="col-md-3 sm-label-right">
                        <strong>Copy From</strong>
                      </div>
                      <div className="col-md-7">
                        <Select
                          options={this.state.configOptions}
                          clearable={false}
                          value={{ value: this.state.command.config.name, label: this.state.command.config.name }}
                          optionRenderer={this.configOptionRenderer}
                          onChange={this.handleConfigChange}
                          onSelectResetsInput={false}
                        />
                      </div>
                    </div>
                  </div>
                )}
                {SETTINGS.feature.pipelineTemplates && hasSelectedATemplate && (
                  <div>
                    <hr />
                    {this.state.templates.length > 0 && (
                      <div className="form-group clearfix">
                        <div className="col-md-3 sm-label-right">Template Source *</div>
                        <div className="col-md-7">
                          <label className="radio-inline">
                            <input
                              type="radio"
                              checked={this.state.useManagedTemplate}
                              onChange={this.handleUseManagedTemplateSelection(true)}
                            />
                            Managed Templates
                          </label>
                          <label className="radio-inline">
                            <input
                              type="radio"
                              checked={!this.state.useManagedTemplate}
                              onChange={this.handleUseManagedTemplateSelection(false)}
                            />
                            URL
                          </label>
                        </div>
                      </div>
                    )}
                    {this.state.useManagedTemplate && !preselectedTemplate && (
                      <ManagedTemplateSelector
                        templates={this.state.templates}
                        onChange={this.handleTemplateSelection}
                        selectedTemplate={this.state.command.template}
                      />
                    )}
                    {!this.state.useManagedTemplate && (
                      <div className="form-group clearfix">
                        {this.state.templates.length === 0 && <div className="col-md-3 sm-label-right">Source URL</div>}
                        <div className={this.state.templates.length ? 'col-md-7 col-md-offset-3' : 'col-md-7'}>
                          <input
                            type="text"
                            className="form-control"
                            value={this.state.templateSourceUrl}
                            onChange={this.handleSourceUrlChange}
                          />
                        </div>
                      </div>
                    )}
                    <TemplateDescription
                      loading={this.state.loadingTemplateFromSource}
                      loadingError={this.state.loadingTemplateFromSourceError}
                      template={this.state.command.template || preselectedTemplate}
                    />
                    {!preselectedTemplate && (
                      <div className="form-group clearfix">
                        <div className="col-md-12">
                          <em>
                            * v1 templates only. For creating pipelines from v2 templates, use the{' '}
                            <UISref to="home.pipeline-templates">
                              <a>Pipeline Templates view.</a>
                            </UISref>
                          </em>
                        </div>
                      </div>
                    )}
                  </div>
                )}
              </form>
            )}
          </Modal.Body>
        )}
        <Modal.Footer>
          <Button onClick={this.close}>Cancel</Button>
          {SETTINGS.feature.pipelineTemplates && hasSelectedATemplate && (
            <SubmitButton
              label="Continue"
              submitting={this.state.submitting}
              isDisabled={!formValid || this.state.submitting || this.state.saveError || this.state.loading}
              onClick={this.submitPipelineTemplateConfig}
            />
          )}
          {!hasSelectedATemplate && (
            <SubmitButton
              label="Create"
              onClick={this.submit}
              submitting={this.state.submitting}
              isDisabled={!formValid || this.state.submitting || this.state.saveError || this.state.loading}
            />
          )}
        </Modal.Footer>
      </Modal>
    );
  }
}
