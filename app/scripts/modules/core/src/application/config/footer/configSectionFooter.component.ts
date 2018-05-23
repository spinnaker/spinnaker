import { IController, IComponentOptions, copy, module, toJson } from 'angular';
import { cloneDeep } from 'lodash';

import { ApplicationWriter } from 'core/application/service/ApplicationWriter';
import { Application } from 'core/application/application.model';

import './configSectionFooter.component.less';

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

    ApplicationWriter.updateApplication(updateCommand).then(() => this.saveSuccess(), () => this.saveError());
  }
}

class ConfigSectionFooterComponent implements IComponentOptions {
  public bindings: any = {
    application: '=',
    config: '=',
    viewState: '=',
    configField: '@',
    revert: '&?',
    afterSave: '&?',
  };

  public controller: any = ConfigSectionFooterController;
  public templateUrl: string = require('./configSectionFooter.component.html');
}

export const CONFIG_SECTION_FOOTER = 'spinnaker.core.application.config.section.footer.component';

module(CONFIG_SECTION_FOOTER, []).component('configSectionFooter', new ConfigSectionFooterComponent());
