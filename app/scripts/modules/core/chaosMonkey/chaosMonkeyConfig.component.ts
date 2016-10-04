import * as _ from 'lodash';
import {module, toJson} from 'angular';

import helpContents from './chaosMonkey.help';
import exceptionsModule from './chaosMonkeyExceptions.component';
import footerModule from './chaosMonkeyConfigFooter.component';

import {Application} from '../application/application.model';

import './chaosMonkeyConfig.component.less';

export class ViewState {
  public originalConfig: ChaosMonkeyConfig;
  public originalStringVal: string;
  public saving: boolean = false;
  public saveError: boolean = false;
  public isDirty: boolean = false;
}

class GroupingOption {
  public key: string;
  public label: string;
}

export class ChaosMonkeyConfig {
  public enabled: boolean = false;
  public meanTimeBetweenKillsInWorkDays: number = 2;
  public minTimeBetweenKillsInWorkDays: number = 1;
  public grouping: string = 'cluster';
  public regionsAreIndependent: boolean = true;
  public exceptions: any[] = [];

  public constructor(config: any) {
    Object.assign(this, config);
  }
}

export class ChaosMonkeyConfigController implements ng.IComponentController {

  public application: Application;
  public config: ChaosMonkeyConfig;
  public chaosEnabled: boolean = false;
  public groupingOptions: GroupingOption[] = [];
  public viewState: ViewState = new ViewState();

  public constructor(private settings: any) {}

  public $onInit(): void {
    if (this.application.notFound) {
      return;
    }
    this.config = new ChaosMonkeyConfig(this.application.attributes.chaosMonkey || {});
    this.viewState.originalConfig = _.cloneDeep(this.config);
    this.viewState.originalStringVal = toJson(this.viewState.originalConfig);
    this.chaosEnabled = this.settings.feature && this.settings.feature.chaosMonkey;
    this.groupingOptions = [
      { key: 'app', label: 'App' },
      { key: 'stack', label: 'Stack' },
      { key: 'cluster', label: 'Cluster' },
    ];
  }

  public configChanged(): void {
    this.viewState.isDirty = this.viewState.originalStringVal !== toJson(this.config);
  }
}

class ChaosMonkeyConfigComponent implements ng.IComponentOptions {
  public bindings: any = {
    application: '=',
  };
  public controller: ng.IComponentController = ChaosMonkeyConfigController;
  public templateUrl: string = require('./chaosMonkeyConfig.component.html');
}

const moduleName = 'spinnaker.core.chaosMonkey.config.component';

module(moduleName, [
  require('../config/settings'),
  exceptionsModule,
  helpContents,
  footerModule,
])
.component('chaosMonkeyConfig', new ChaosMonkeyConfigComponent());

export default moduleName;
