import {module, copy, toJson} from 'angular';
import * as _ from 'lodash';

import {Application} from '../application/application.model.ts';
import {ViewState} from './chaosMonkeyConfig.component.ts';

import './chaosMonkeyConfigFooter.component.less';

export class ChaosMonkeyConfigFooterController implements ng.IComponentController {

  public viewState: ViewState;
  public application: Application;
  public config: any;

  static get inject() { return ['applicationWriter']; }

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
    this.application.attributes.chaosMonkey = this.config;
  }

  private saveError(): void {
    this.viewState.saving = false;
    this.viewState.saveError = true;
  }

  public save(): void {
    this.viewState.saving = true;
    this.viewState.saveError = false;
    this.applicationWriter.updateApplication({
      name: this.application.name,
      accounts: this.application.attributes.accounts,
      chaosMonkey: this.config
    }).then(() => this.saveSuccess(), () => this.saveError());
  }
}

class ChaosMonkeyConfigFooterComponent implements ng.IComponentOptions {
  public bindings: any = {
    application: '=',
    config: '=',
    viewState: '='
  };

  public controller: ng.IComponentController = ChaosMonkeyConfigFooterController;
  public templateUrl: string = require('./chaosMonkeyConfigFooter.component.html');
}

const moduleName = 'spinnaker.core.chaosMonkey.config.footer.component';

module(moduleName, [
  require('../application/service/applications.write.service.js'),
])
.component('chaosMonkeyConfigFooter', new ChaosMonkeyConfigFooterComponent());

export default moduleName;
