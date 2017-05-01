import * as React from 'react';
import {Button, Modal} from 'react-bootstrap';
import * as Select from 'react-select';
import autoBindMethods from 'class-autobind-decorator';

import {$log} from 'ngimport';
import {IHttpPromiseCallbackArg} from 'angular';
import {cloneDeep} from 'lodash';
import {pipelineConfigService} from 'core/pipeline/config/services/pipelineConfig.service';
import {Application} from 'core/application/application.model';
import {IPipeline} from 'core/domain/IPipeline';
import {SubmitButton} from 'core/modal/buttons/SubmitButton';

import './createPipelineModal.less';

interface ICreatePipelineModalState {
  submitting: boolean;
  saveError: boolean;
  errorMessage: string;
  command: ICreatePipelineCommand;
  existingNames: string[];
  templates: Partial<IPipeline>[];
  templateOptions: Select.Option[];
}

interface ICreatePipelineCommand {
  parallel: boolean;
  strategy: boolean;
  name: string;
  template: Partial<IPipeline>;
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

  private getDefaultTemplate(): Partial<IPipeline> {
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
    const defaultTemplate = this.getDefaultTemplate();
    const templates: Partial<IPipeline>[] = [defaultTemplate].concat(this.props.application.getDataSource('pipelineConfigs').data);
    const templateOptions: Select.Option[] = templates.map(template => ({value: template.name, label: template.name}));
    const existingNames: string[] = [defaultTemplate]
      .concat(this.props.application.getDataSource('pipelineConfigs').data)
      .concat(this.props.application.strategyConfigs.data)
      .map(config => config.name);

    return {
      submitting: false,
      saveError: false,
      errorMessage: null,
      templates: templates,
      templateOptions: templateOptions,
      existingNames: existingNames,
      command: {parallel: true, strategy: false, name: '', template: defaultTemplate},
    };
  }

  public submit(): void {
    const command = cloneDeep(this.state.command);
    const template: Partial<IPipeline> = command.strategy ? this.getDefaultTemplate() : command.template;

    template.name = command.name;
    template.index = this.props.application.getDataSource('pipelineConfigs').data.length;
    delete template.id;

    if (command.strategy) {
      template.strategy = true;
      template.limitConcurrent = false;
    }

    this.setState({submitting: true});
    pipelineConfigService.savePipeline(template as IPipeline)
      .then(() => this.onSaveSuccess(template as IPipeline), this.onSaveFailure);
  }

  private onSaveSuccess(template: IPipeline): void {
    const application = this.props.application;
    template.isNew = true;
    application.getDataSource('pipelineConfigs').refresh().then(() => {
      const newPipeline = (template.strategy ?
                          (application.strategyConfigs.data as IPipeline[]) :
                           application.getDataSource('pipelineConfigs').data).find(config => config.name === template.name);
      if (!newPipeline) {
        $log.warn('Could not find new pipeline after save succeeded.');
        this.setState({
          saveError: true,
          errorMessage: 'Sorry, there was an error retrieving your new pipeline. Please refresh the browser.',
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
      errorMessage: (response && response.data && response.data.message) || 'No message provided',
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

  private handleTemplateChange(option: Select.Option): void {
    const template = this.state.templates.find(t => t.name === option.value);
    this.setState({command: Object.assign({}, this.state.command, {template})});
  }

  private handleErrorDismiss(): void {
    this.setState({saveError: false});
  }

  private templateOptionRenderer(option: Select.Option) {
    const template = this.state.templates.find(t => t.name === option.value);
    return (
      <div>
        <h5>{template.name}</h5>
        {template.stages.length > 0 && (
          <div className="small">
            <b>Stages: </b>
            <ul>
              {template.stages.map(stage => (<li key={stage.refId}>{stage.name || stage.type}</li>))}
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

  public render() {
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
        <Modal.Body>
          {this.state.saveError && (
            <div className="alert alert-danger">
              <p>Could not save pipeline.</p>
              <p><b>Reason: </b> {this.state.errorMessage}</p>
              <p><a onClick={this.handleErrorDismiss}>[dismiss]</a></p>
            </div>
          )}
          {!this.state.saveError && (
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
              {(this.state.templates.length > 1 && !this.state.command.strategy) && (
                <div className="form-group clearfix">
                  <div className="col-md-3 sm-label-right">
                    <b>Copy From</b>
                  </div>
                  <div className="col-md-7">
                    <Select
                      options={this.state.templateOptions}
                      clearable={false}
                      value={{value: this.state.command.template.name, label: this.state.command.template.name}}
                      optionRenderer={this.templateOptionRenderer}
                      onChange={this.handleTemplateChange}
                    />
                  </div>
                </div>
              )}
            </form>
          )}
        </Modal.Body>
        <Modal.Footer>
          <Button onClick={this.close}>Cancel</Button>
          <SubmitButton
            label="Create"
            onClick={this.submit}
            submitting={this.state.submitting}
            isDisabled={!formValid || this.state.submitting || this.state.saveError}
          />
        </Modal.Footer>
      </Modal>
    );
  }
}
