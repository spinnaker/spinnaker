import { IController, IComponentOptions, IPromise, module } from 'angular';

import { groupBy, sortBy } from 'lodash';

import { IServerGroup } from 'core/domain';
import { Application } from 'core/application';
import { PROVIDER_SERVICE_DELEGATE, ProviderServiceDelegate } from 'core/cloudProvider/providerService.delegate';
import { SERVER_GROUP_READER, ServerGroupReader } from 'core/serverGroup';

export interface IDeployTemplate {
  label?: string,
  serverGroup: IServerGroup,
  cluster: string,
  account?: string,
  region?: string,
  serverGroupName?: string,
}

export interface ITemplateSelectionText {
  copied: string[],
  notCopied: string[],
  additionalCopyText: string,
}

// TODO: move this to a better place as we convert the server group wizard modals to TS
export interface IParentState {
  loaded: boolean,
}

export class DeployInitializerController implements IController {
  public templates: IDeployTemplate[] = [];

  public application: Application;
  public command: any;
  public onTemplateSelected: () => void;
  public selectedTemplate: IDeployTemplate;
  public cloudProvider: string;
  public parentState: IParentState;
  public templateSelectionText: ITemplateSelectionText;

  private noTemplate: IDeployTemplate = { label: 'None', serverGroup: null, cluster: null };

  constructor(private providerServiceDelegate: ProviderServiceDelegate, private serverGroupReader: ServerGroupReader) {
    'ngInject';
  }

  public $onInit(): void {
    const { viewState } = this.command;
    if (!viewState.disableNoTemplateSelection) {
      this.templates.push(this.noTemplate);
      this.selectedTemplate = this.noTemplate;
    }

    const serverGroups: IServerGroup[] = this.application.getDataSource('serverGroups').data
      .filter((s: IServerGroup) => s.cloudProvider === this.cloudProvider && s.category === 'serverGroup');

    const grouped = groupBy(serverGroups, (serverGroup) => [serverGroup.cluster, serverGroup.account, serverGroup.region].join(':'));

    Object.keys(grouped).forEach(key => {
      const latest = sortBy(grouped[key], 'name').pop();
      this.templates.push({
        cluster: latest.cluster,
        account: latest.account,
        region: latest.region,
        serverGroupName: latest.name,
        serverGroup: latest
      });
    });

    if (this.templates.length === 1) {
      this.useTemplate();
    }
  }

  private applyCommandToScope(command: any) {
    const { viewState } = command;
    const baseCommand = this.command;
    viewState.disableImageSelection = true;
    viewState.disableStrategySelection = baseCommand.viewState.disableStrategySelection || false;
    viewState.imageId = null;
    viewState.readOnlyFields = baseCommand.viewState.readOnlyFields || {};
    viewState.submitButtonLabel = 'Add';
    viewState.hideClusterNamePreview = baseCommand.viewState.hideClusterNamePreview || false;
    viewState.templatingEnabled = true;
    Object.assign(command, baseCommand.viewState.overrides || {});
    Object.assign(baseCommand, command);
  }

  private buildCommandFromTemplate(serverGroup: IServerGroup): IPromise<any> {
    const commandBuilder: any = this.providerServiceDelegate.getDelegate(this.cloudProvider, 'serverGroup.commandBuilder');
    return this.serverGroupReader.getServerGroup(this.application.name, serverGroup.account, serverGroup.region, serverGroup.name)
      .then(details => {
        details.account = serverGroup.account;
        return commandBuilder.buildServerGroupCommandFromExisting(this.application, details, 'editPipeline');
    });
  }

  private buildEmptyCommand(): IPromise<any> {
    const commandBuilder: any = this.providerServiceDelegate.getDelegate(this.cloudProvider, 'serverGroup.commandBuilder');
    return commandBuilder.buildNewServerGroupCommand(this.application, { mode: 'createPipeline' });
  }

  private selectTemplate(): IPromise<void> {
    const buildCommand = this.selectedTemplate === this.noTemplate ?
      this.buildEmptyCommand() :
      this.buildCommandFromTemplate(this.selectedTemplate.serverGroup);
    return buildCommand.then((command: any) => this.applyCommandToScope(command));
  }

  public useTemplate(): void {
    this.parentState.loaded = false;
    this.selectTemplate().then(() => this.onTemplateSelected());
  }
}

const component: IComponentOptions = {
  bindings: {
    application: '<',
    cloudProvider: '@',
    command: '<',
    onTemplateSelected: '&',
    parentState: '<',
    templateSelectionText: '<',
  },
  templateUrl: require('./deployInitializer.component.html'),
  controller: DeployInitializerController
};


export const DEPLOY_INITIALIZER_COMPONENT = 'spinnaker.core.serverGroup.configure.deployInitializer';
module(DEPLOY_INITIALIZER_COMPONENT, [
  PROVIDER_SERVICE_DELEGATE,
  SERVER_GROUP_READER,
]).component('deployInitializer', component);
