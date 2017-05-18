import * as React from 'react';
import {Button, Modal} from 'react-bootstrap';
import * as Select from 'react-select';
import autoBindMethods from 'class-autobind-decorator';
import {$log} from 'ngimport';
import {IHttpPromiseCallbackArg} from 'angular';
import {cloneDeep, uniqBy} from 'lodash';
import {Application} from 'core/application/application.model';
import {IPipeline} from 'core/domain/IPipeline';
import {SubmitButton} from 'core/modal/buttons/SubmitButton';
import {ReactInjector} from 'core/reactShims';
import {SETTINGS} from 'core/config/settings';
import {IPipelineTemplate} from 'core/pipeline/config/templates/pipelineTemplate.service';
import {TemplateDescription} from './TemplateDescription';
import {TemplateSelector} from './TemplateSelector';

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
  configs: Partial<IPipeline>[];
  configOptions: Select.Option[];
  templates: IPipelineTemplate[]
  useTemplate: boolean;
}

interface ICreatePipelineCommand {
  parallel: boolean;
  strategy: boolean;
  name: string;
  config: Partial<IPipeline>;
  template: IPipelineTemplate;
}

export interface ICreatePipelineModalProps {
  application: Application;
  show: boolean;
  showCallback: (show: boolean) => void;
  pipelineSavedCallback: (pipelineId: string) => void;
}

@autoBindMethods
export class CreatePipelineModal extends React.Component<ICreatePipelineModalProps, ICreatePipelineModalState> {

  constructor(props: ICreatePipelineModalProps) {
    super(props);
    this.state = this.getDefaultState();
  }

  public componentWillUpdate(nextProps: ICreatePipelineModalProps): void {
    if (nextProps.show && !this.props.show && !this.state.loading) {
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
      parallel: true,
      executionEngine: 'v2',
    };
  }

  private getDefaultState(): ICreatePipelineModalState {
    const defaultConfig = this.getDefaultConfig();
    const configs: Partial<IPipeline>[] = [defaultConfig].concat(this.props.application.getDataSource('pipelineConfigs').data);
    const configOptions: Select.Option[] = configs.map(config => ({value: config.name, label: config.name}));
    const existingNames: string[] = [defaultConfig]
      .concat(this.props.application.getDataSource('pipelineConfigs').data)
      .concat(this.props.application.strategyConfigs.data)
      .map(config => config.name);

    return {
      submitting: false,
      saveError: false,
      saveErrorMessage: null,
      loading: false,
      loadError: false,
      loadErrorMessage: null,
      configs: configs,
      configOptions: configOptions,
      templates: [],
      existingNames: existingNames,
      command: {parallel: true, strategy: false, name: '', config: defaultConfig, template: null},
      useTemplate: false,
    };
  }

  public submit(): void {
    const command = cloneDeep(this.state.command);
    const config: Partial<IPipeline> = command.strategy ? this.getDefaultConfig() : command.config;

    config.name = command.name;
    config.index = this.props.application.getDataSource('pipelineConfigs').data.length;
    delete config.id;

    if (command.strategy) {
      config.strategy = true;
      config.limitConcurrent = false;
    }

    this.setState({submitting: true});
    ReactInjector.pipelineConfigService.savePipeline(config as IPipeline)
      .then(() => this.onSaveSuccess(config as IPipeline), this.onSaveFailure);
  }

  private onSaveSuccess(config: IPipeline): void {
    const application = this.props.application;
    config.isNew = true;
    application.getDataSource('pipelineConfigs').refresh().then(() => {
      const newPipeline = (config.strategy ?
                          (application.strategyConfigs.data as IPipeline[]) :
                           application.getDataSource('pipelineConfigs').data).find(_config => _config.name === config.name);
      if (!newPipeline) {
        $log.warn('Could not find new pipeline after save succeeded.');
        this.setState({
          saveError: true,
          saveErrorMessage: 'Sorry, there was an error retrieving your new pipeline. Please refresh the browser.',
          submitting: false,
        });
      } else {
        this.setState(this.getDefaultState());
        this.props.pipelineSavedCallback(newPipeline.id);
      }
    });
  }

  private onSaveFailure(response: IHttpPromiseCallbackArg<{message: string}>): void {
    $log.warn(response);
    this.setState({
      submitting: false,
      saveError: true,
      saveErrorMessage: (response && response.data && response.data.message) || 'No message provided',
    });
  }

  public close(): void {
    this.setState(this.getDefaultState());
    this.props.showCallback(false);
  }

  private handleTypeChange(option: Select.Option): void {
    this.setState({command: Object.assign({}, this.state.command, {strategy: option.value})});
  }

  private handleNameChange(e: React.ChangeEvent<HTMLInputElement>): void {
    this.setState({command: Object.assign({}, this.state.command, {name: e.target.value})});
  }

  private handleConfigChange(option: Select.Option): void {
    const config = this.state.configs.find(t => t.name === option.value);
    this.setState({command: Object.assign({}, this.state.command, {config})});
  }

  private handleSaveErrorDismiss(): void {
    this.setState({saveError: false});
  }

  private handleLoadErrorDismiss(): void {
    this.setState({loadError: false});
  }

  private handleTemplateSelection(template: IPipelineTemplate): void {
    this.setState({command: Object.assign({}, this.state.command, {template})});
  }

  private handleUseTemplateSelection(useTemplate: boolean): () => void {
    return () => this.setState({useTemplate});
  }

  private configOptionRenderer(option: Select.Option) {
    const config = this.state.configs.find(t => t.name === option.value);
    return (
      <div>
        <h5>{config.name}</h5>
        {config.stages.length > 0 && (
          <div className="small">
            <b>Stages: </b>
            <ul>
              {config.stages.map(stage => (<li key={stage.refId}>{stage.name || stage.type}</li>))}
            </ul>
          </div>
        )}
      </div>
    );
  }

  public validateNameCharacters(): boolean {
    return /^[^\\\^/^?^%^#]*$/.test(this.state.command.name); // Verify name does not include: \, ^, ?, %, #
  }

  public validateNameIsUnique(): boolean {
    return this.state.existingNames.every(name => name !== this.state.command.name);
  }

  private openPipelineTemplateConfigModal(): void {
    this.close();
    ReactInjector.modalService.open({
      size: 'lg',
      templateUrl: require('core/pipeline/config/templates/configurePipelineTemplateModal.html'),
      controller: 'ConfigurePipelineTemplateModalCtrl as ctrl',
      resolve: {
        application: () => this.props.application,
        template: (): IPipelineTemplate => this.state.command.template,
        pipelineName: () => this.state.command.name,
        variables: (): void => null,
      }
    });
  }

  public loadPipelineTemplates(): void {
    if (SETTINGS.feature.pipelineTemplates) {
      this.setState({loading: true});
      ReactInjector.pipelineTemplateService.getPipelineTemplatesByScopes([this.props.application.name, 'global'])
        .then(templates => {
          templates = uniqBy(templates, 'id');
          this.setState({templates, loading: false});
        })
        .catch((response: IHttpPromiseCallbackArg<{message: string}>) => {
          this.setState({
            loadError: true,
            loadErrorMessage: (response && response.data && response.data.message) || 'No message provided',
            loading: false,
          });
        });
    }
  }

  public render() {
    const { Spinner } = ReactInjector;
    const nameHasError: boolean = !this.validateNameCharacters();
    const nameIsNotUnique: boolean = !this.validateNameIsUnique();
    const formValid = !nameHasError &&
                      !nameIsNotUnique &&
                      this.state.command.name.length > 0;

    return (
      <Modal show={this.props.show} onHide={this.close} className="create-pipeline-modal-overflow-visible" backdrop="static">
        <Modal.Header closeButton={true}>
          <Modal.Title>Create New {this.state.command.strategy ? 'Strategy' : 'Pipeline'}</Modal.Title>
        </Modal.Header>
        {this.state.loading && (
          <Modal.Body style={{height: '200px'}}><Spinner radius={25} width={6} length={16} /></Modal.Body>
        )}
        {!this.state.loading && (
          <Modal.Body>
            {this.state.loadError && (
              <div className="alert alert-danger">
                <p>Could not load pipeline templates.</p>
                <p><strong>Reason: </strong> {this.state.loadErrorMessage}</p>
                <p><a onClick={this.handleLoadErrorDismiss}>[dismiss]</a></p>
              </div>
            )}
            {this.state.saveError && (
              <div className="alert alert-danger">
                <p>Could not save pipeline.</p>
                <p><strong>Reason: </strong> {this.state.saveErrorMessage}</p>
                <p><a onClick={this.handleSaveErrorDismiss}>[dismiss]</a></p>
              </div>
            )}
            {!(this.state.saveError || this.state.loadError) && (
              <form role="form" name="form" className="clearfix">
                <div className="form-group clearfix">
                  <div className="col-md-3 sm-label-right">
                    <b>Type</b>
                  </div>
                  <div className="col-md-7">
                    <Select
                      options={[{label: 'Pipeline', value: false}, {label: 'Strategy', value: true}]}
                      clearable={false}
                      value={this.state.command.strategy ? {label: 'Strategy'} : {label: 'Pipeline'}}
                      onChange={this.handleTypeChange}
                    />
                  </div>
                </div>
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
                    />
                  </div>
                </div>
                {nameHasError && (
                  <div className="form-group row slide-in">
                    <div className="col-sm-9 col-sm-offset-3 error-message">
                      <div>
                        {this.state.command.strategy ? 'Strategy' : 'Pipeline'} name cannot contain any of the following characters:
                      </div>
                      <code>/  \  ?  %  #</code>
                    </div>
                  </div>
                )}
                {nameIsNotUnique && (
                  <div className="form-group row slide-in">
                    <div className="col-sm-9 col-sm-offset-3 error-message">
                      <span>There is already a {this.state.command.strategy ? 'strategy' : 'pipeline'} with that name.</span>
                    </div>
                  </div>
                )}
                {SETTINGS.feature.pipelineTemplates && this.state.templates.length > 0 && !this.state.command.strategy && (
                  <div className="form-group clearfix">
                    <div className="col-md-3 sm-label-right">
                      <strong>Create From</strong>
                    </div>
                    <div className="col-md-7">
                      <label className="radio-inline">
                        <input type="radio" checked={!this.state.useTemplate} onChange={this.handleUseTemplateSelection(false)}/>
                        Pipeline
                      </label>
                      <label className="radio-inline">
                        <input type="radio" checked={this.state.useTemplate} onChange={this.handleUseTemplateSelection(true)}/>
                        Template
                      </label>
                    </div>
                  </div>
                )}
                {(this.state.configs.length > 1 && !this.state.command.strategy && !this.state.useTemplate) && (
                  <div>
                    {SETTINGS.feature.pipelineTemplates && <hr/>}
                    <div className="form-group clearfix">
                      <div className="col-md-3 sm-label-right">
                        <strong>Copy From</strong>
                      </div>
                      <div className="col-md-7">
                        <Select
                          options={this.state.configOptions}
                          clearable={false}
                          value={{value: this.state.command.config.name, label: this.state.command.config.name}}
                          optionRenderer={this.configOptionRenderer}
                          onChange={this.handleConfigChange}
                        />
                      </div>
                    </div>
                  </div>
                )}
                {SETTINGS.feature.pipelineTemplates && this.state.useTemplate && (
                  <div>
                    <hr/>
                    <TemplateSelector
                      templates={this.state.templates}
                      onChange={this.handleTemplateSelection}
                      selectedTemplate={this.state.command.template}
                    />
                    {this.state.command.template && (
                      <TemplateDescription templateMetadata={this.state.command.template.metadata}/>
                    )}
                  </div>
                )}
              </form>
            )}
        </Modal.Body>)}
        <Modal.Footer>
          <Button onClick={this.close}>Cancel</Button>
          {SETTINGS.feature.pipelineTemplates && this.state.useTemplate && (
            <SubmitButton
              isDisabled={!formValid}
              label="Define Template Parameters"
              submitting={false}
              onClick={this.openPipelineTemplateConfigModal}
            />
          )}
          {!this.state.useTemplate && (
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
