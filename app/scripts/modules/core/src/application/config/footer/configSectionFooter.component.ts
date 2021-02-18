import { copy, IComponentOptions, IController, module, toJson } from 'angular';
import { cloneDeep } from 'lodash';

import { Application } from '../../application.model';
import { ApplicationWriter } from '../../service/ApplicationWriter';

export interface IConfigSectionFooterViewState {
  originalConfig: any;
  originalStringVal: string;
  saving: boolean;
  saveError: boolean;
  isDirty: boolean;
}

export class ConfigSectionFooterController implements IController {
  public viewState: IConfigSectionFooterViewState;
  public application: Application;
  public config: any;
  public configField: string;
  public saveDisabled: boolean;

  public revert(): void {
    copy(this.viewState.originalConfig, this.config);
    this.viewState.isDirty = false;
  }

  private saveSuccess(): void {
    this.viewState.originalConfig = cloneDeep(this.config);
    this.viewState.originalStringVal = toJson(this.config);
    this.viewState.isDirty = false;
    this.viewState.saving = false;
    this.application.attributes[this.configField] = this.config;
  }

  private saveError(): void {
    this.viewState.saving = false;
    this.viewState.saveError = true;
  }

  public save(): void {
    this.viewState.saving = true;
    this.viewState.saveError = false;

    const updateCommand: any = {
      name: this.application.name,
      accounts: this.application.attributes.accounts,
    };
    updateCommand[this.configField] = this.config;

    ApplicationWriter.updateApplication(updateCommand).then(
      () => this.saveSuccess(),
      () => this.saveError(),
    );
  }
}

const configSectionFooterComponent: IComponentOptions = {
  bindings: {
    application: '=',
    config: '=',
    viewState: '=',
    configField: '@',
    revert: '&?',
    afterSave: '&?',
    saveDisabled: '<',
  },
  controller: ConfigSectionFooterController,
  templateUrl: require('./configSectionFooter.component.html'),
};

export const CONFIG_SECTION_FOOTER = 'spinnaker.core.application.config.section.footer.component';

module(CONFIG_SECTION_FOOTER, []).component('configSectionFooter', configSectionFooterComponent);
