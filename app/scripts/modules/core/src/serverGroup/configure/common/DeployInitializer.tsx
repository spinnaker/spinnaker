import { groupBy, sortBy } from 'lodash';
import React from 'react';
import { Modal } from 'react-bootstrap';
import { Option } from 'react-select';

import { AccountTag } from '../../../account';
import { Application } from '../../../application';
import { IDeployTemplate, ITemplateSelectionText } from './deployInitializer.component';
import { IServerGroup } from '../../../domain';
import { ModalClose } from '../../../modal';
import { TetheredSelect } from '../../../presentation/TetheredSelect';
import { ReactInjector } from '../../../reactShims';
import { IServerGroupCommand } from './serverGroupCommandBuilder.service';
import { ServerGroupReader } from '../../serverGroupReader.service';

export interface IDeployInitializerProps {
  application: Application;
  cloudProvider: string;
  command: IServerGroupCommand;
  onDismiss: () => void;
  onTemplateSelected: () => void;
  templateSelectionText?: ITemplateSelectionText;
}

export interface IDeployInitializerState {
  selectedTemplate: IDeployTemplate;
  templates: IDeployTemplate[];
}

export class DeployInitializer extends React.Component<IDeployInitializerProps, IDeployInitializerState> {
  private noTemplate: IDeployTemplate = { label: 'None', serverGroup: null, cluster: null };

  constructor(props: IDeployInitializerProps) {
    super(props);

    const templates: IDeployTemplate[] = [];
    let selectedTemplate: IDeployTemplate;

    const { viewState } = props.command;
    if (!viewState.disableNoTemplateSelection) {
      templates.push(this.noTemplate);
    }

    selectedTemplate = this.noTemplate;

    const serverGroups: IServerGroup[] = props.application
      .getDataSource('serverGroups')
      .data.filter((s: IServerGroup) => s.cloudProvider === props.cloudProvider && s.category === 'serverGroup');

    const grouped = groupBy(serverGroups, (serverGroup) =>
      [serverGroup.cluster, serverGroup.account, serverGroup.region].join(':'),
    );

    Object.keys(grouped).forEach((key) => {
      const latest = sortBy(grouped[key], 'name').pop();
      templates.push({
        cluster: latest.cluster,
        account: latest.account,
        region: latest.region,
        serverGroupName: latest.name,
        serverGroup: latest,
        key: [latest.account, latest.region, latest.name].join(':'),
      });
    });

    if (viewState.disableNoTemplateSelection && templates.length === 1) {
      selectedTemplate = templates[0];
    } else if (!viewState.disableNoTemplateSelection && templates.length === 2) {
      selectedTemplate = templates[1];
    }

    this.state = {
      selectedTemplate,
      templates,
    };
  }

  private applyCommandToScope(command: any) {
    const { viewState } = command;
    const baseCommand = this.props.command;
    viewState.disableImageSelection = true;
    viewState.showImageSourceSelector = true;
    viewState.disableStrategySelection = baseCommand.viewState.disableStrategySelection || false;
    viewState.expectedArtifacts = baseCommand.viewState.expectedArtifacts || [];
    viewState.imageId = null;
    viewState.readOnlyFields = baseCommand.viewState.readOnlyFields || {};
    viewState.submitButtonLabel = 'Add';
    viewState.hideClusterNamePreview = baseCommand.viewState.hideClusterNamePreview || false;
    viewState.templatingEnabled = true;
    viewState.imageSourceText = baseCommand.viewState.imageSourceText;
    Object.assign(command, baseCommand.viewState.overrides || {});
    Object.assign(baseCommand, command);
  }

  private buildCommandFromTemplate(serverGroup: IServerGroup): PromiseLike<any> {
    const { application, cloudProvider } = this.props;

    const commandBuilder: any = ReactInjector.providerServiceDelegate.getDelegate(
      cloudProvider,
      'serverGroup.commandBuilder',
    );
    return ServerGroupReader.getServerGroup(
      application.name,
      serverGroup.account,
      serverGroup.region,
      serverGroup.name,
    ).then((details) => {
      details.account = serverGroup.account;
      return commandBuilder.buildServerGroupCommandFromExisting(application, details, 'editPipeline');
    });
  }

  private buildEmptyCommand = (): PromiseLike<any> => {
    const { application, cloudProvider } = this.props;
    const commandBuilder: any = ReactInjector.providerServiceDelegate.getDelegate(
      cloudProvider,
      'serverGroup.commandBuilder',
    );
    return commandBuilder.buildNewServerGroupCommand(application, { mode: 'createPipeline' });
  };

  private selectTemplate = (): PromiseLike<void> => {
    const buildCommand =
      this.state.selectedTemplate === this.noTemplate
        ? this.buildEmptyCommand()
        : this.buildCommandFromTemplate(this.state.selectedTemplate.serverGroup);
    return buildCommand.then((command: any) => this.applyCommandToScope(command));
  };

  public useTemplate = (): void => {
    this.selectTemplate().then(() => this.props.onTemplateSelected());
  };

  public templateChanged = (option: Option) => {
    this.setState({ selectedTemplate: option as IDeployTemplate });
  };

  public componentDidMount() {
    if (this.state.templates.length === 1) {
      this.useTemplate();
    }
  }

  public render() {
    const { command, onDismiss, templateSelectionText } = this.props;
    const { selectedTemplate, templates } = this.state;

    return (
      <div>
        <ModalClose dismiss={onDismiss} />
        <div>
          <Modal.Header>
            <Modal.Title>Template Selection</Modal.Title>
          </Modal.Header>
          <Modal.Body>
            <form className="form-horizontal">
              <div className="form-group">
                <div className="col-md-4 col-md-offset-1 sm-label-left">
                  <b>Copy configuration from</b>
                </div>
              </div>
              <div className="form-group">
                <div className="col-md-6 col-md-offset-1">
                  <TetheredSelect
                    value={selectedTemplate.key}
                    placeholder="Select..."
                    valueRenderer={this.templateValueRenderer}
                    optionRenderer={this.templateOptionRenderer}
                    options={templates}
                    valueKey="key"
                    onChange={this.templateChanged}
                    clearable={false}
                  />
                </div>
              </div>
              {command.viewState.customTemplateMessage && (
                <div className="form-group">
                  <p className="col-md-10 col-md-offset-1">{command.viewState.customTemplateMessage}</p>
                </div>
              )}
              {selectedTemplate.serverGroup && templateSelectionText && (
                <div className="form-group" style={{ marginTop: '20px' }}>
                  <div className="col-md-10 col-md-offset-1 well">
                    {templateSelectionText.copied.length > 0 && (
                      <div>
                        These fields <strong>will be</strong> copied over from the most recent server group:
                        <ul>
                          {templateSelectionText.copied.map((text, i) => (
                            <li key={i}>{text}</li>
                          ))}
                        </ul>
                      </div>
                    )}
                    {templateSelectionText.notCopied.length > 0 && (
                      <div>
                        These fields <strong>will NOT</strong> be copied over, and will be reset to defaults:
                        <ul>
                          {templateSelectionText.notCopied.map((text, i) => (
                            <li key={i}>{text}</li>
                          ))}
                        </ul>
                      </div>
                    )}
                    {templateSelectionText.additionalCopyText && <div>{templateSelectionText.additionalCopyText}</div>}
                  </div>
                </div>
              )}
            </form>
          </Modal.Body>
          <div className="modal-footer">
            {(selectedTemplate.serverGroup || !command.viewState.disableNoTemplateSelection) && (
              <button className="btn btn-primary" onClick={this.useTemplate}>
                {selectedTemplate.serverGroup && <span>Use this template</span>}
                {!selectedTemplate.serverGroup && <span>Continue without a template</span>}
                <span className="glyphicon glyphicon-chevron-right" />
              </button>
            )}
          </div>
        </div>
      </div>
    );
  }

  private templateValueRenderer = (option: Option) => {
    if (option.label) {
      return <span>{option.label}</span>;
    }

    return (
      <span>
        <AccountTag account={option.account} />
        {option.serverGroup && <span> {option.serverGroupName}</span>} ({option.region})
      </span>
    );
  };

  private templateOptionRenderer = (option: Option) => {
    return (
      <>
        {!option.label && (
          <h5>
            <AccountTag account={option.account} /> {option.cluster} ({option.region})
          </h5>
        )}
        {option.label && <h5>{option.label}</h5>}
        {option.serverGroup && (
          <div>
            <b>Most recent server group: </b> {option.serverGroupName}
          </div>
        )}
      </>
    );
  };
}
