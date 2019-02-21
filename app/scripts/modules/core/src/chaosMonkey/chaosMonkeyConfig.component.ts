import { IController, module, toJson } from 'angular';
import * as _ from 'lodash';

import { SETTINGS } from 'core/config/settings';

import { CLUSTER_MATCHES_COMPONENT } from 'core/widgets/cluster/clusterMatches.component';
import { Application, CONFIG_SECTION_FOOTER, IConfigSectionFooterViewState } from 'core/application';
import './chaosMonkey.help';
import { CHAOS_MONKEY_EXCEPTIONS_COMPONENT } from './chaosMonkeyExceptions.component';

import './chaosMonkeyConfig.component.less';
import { IClusterMatchRule } from 'core/cluster/ClusterRuleMatcher';

export class ChaosMonkeyGroupingOption {
  public key: string;
  public label: string;
}

export interface IChaosMonkeyExceptionRule extends IClusterMatchRule {
  region: string;
}

export class ChaosMonkeyConfig {
  [key: string]: any;
  public enabled = false;
  public meanTimeBetweenKillsInWorkDays = 2;
  public minTimeBetweenKillsInWorkDays = 1;
  public grouping = 'cluster';
  public regionsAreIndependent = true;
  public exceptions: IChaosMonkeyExceptionRule[] = [];

  public constructor(config: any) {
    Object.assign(this, config);
  }
}

export class ChaosMonkeyConfigController implements IController {
  public application: Application;
  public config: ChaosMonkeyConfig;
  public chaosEnabled = false;
  public groupingOptions: ChaosMonkeyGroupingOption[] = [];
  public viewState: IConfigSectionFooterViewState = {
    originalConfig: null,
    originalStringVal: null,
    saving: false,
    saveError: false,
    isDirty: false,
  };

  public $onInit(): void {
    if (this.application.notFound) {
      return;
    }
    this.config = new ChaosMonkeyConfig(this.application.attributes.chaosMonkey || {});
    this.viewState.originalConfig = _.cloneDeep(this.config);
    this.viewState.originalStringVal = toJson(this.viewState.originalConfig);
    this.chaosEnabled = SETTINGS.feature.chaosMonkey;
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

const chaosMonkeyConfigComponent: ng.IComponentOptions = {
  bindings: {
    application: '=',
  },
  controller: ChaosMonkeyConfigController,
  templateUrl: require('./chaosMonkeyConfig.component.html')
};

export const CHAOS_MONKEY_CONFIG_COMPONENT = 'spinnaker.core.chaosMonkey.config.component';
module(CHAOS_MONKEY_CONFIG_COMPONENT, [
  CLUSTER_MATCHES_COMPONENT,
  CHAOS_MONKEY_EXCEPTIONS_COMPONENT,
  CONFIG_SECTION_FOOTER,
]).component('chaosMonkeyConfig', chaosMonkeyConfigComponent);
