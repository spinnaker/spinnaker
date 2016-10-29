import {module, copy, toJson} from 'angular';
import * as _ from 'lodash';

import {Application} from '../../application.model';

import './configSectionFooter.component.less';

export interface IViewState {
  originalConfig: any;
  originalStringVal: string;
  saving: boolean;
  saveError: boolean;
  isDirty: boolean;
}

export class ConfigSectionFooterController implements ng.IComponentController {

  public viewState: IViewState;
  public application: Application;
  public config: any;
  public configField: string;

  static get $inject() { return ['applicationWriter']; }

  public constructor(private applicationWriter: any) {}

  public revert(): void {
    copy(this.viewState.originalConfig, this.config);
    this.viewState.isDirty = false;
  }

  private saveSuccess(): void {
    this.viewState.originalConfig = _.cloneDeep(this.config);
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

    this.applicationWriter.updateApplication(updateCommand).then(() => this.saveSuccess(), () => this.saveError());
  }
}

class ConfigSectionFooterComponent implements ng.IComponentOptions {
  public bindings: any = {
    application: '=',
    config: '=',
    viewState: '=',
    configField: '@',
    revert: '&?',
    afterSave: '&?',
  };

  public controller: ng.IComponentController = ConfigSectionFooterController;
  public templateUrl: string = require('./configSectionFooter.component.html');
}

export const CONFIG_SECTION_FOOTER = 'spinnaker.core.application.config.section.footer.component';

module(CONFIG_SECTION_FOOTER, [
  require('../../service/applications.write.service.js'),
])
.component('configSectionFooter', new ConfigSectionFooterComponent());
