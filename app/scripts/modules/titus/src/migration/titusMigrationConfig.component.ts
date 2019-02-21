import { module, toJson } from 'angular';
import { cloneDeep } from 'lodash';

import { AccountService, Application, CONFIG_SECTION_FOOTER, IConfigSectionFooterViewState } from '@spinnaker/core';

import { TITUS_MIGRATION_CONFIGURER_COMPONENT } from './titusMigrationConfigurer.component';

import './titusMigrationConfig.component.less';

export interface ITitusMigrationViewState extends IConfigSectionFooterViewState {
  accountsLoaded: boolean;
}

export type MigrationStrategyType = 'rollingPush' | 'pipeline';

/**
 * Marker interface for migration strategies
 */
export interface IMigrationStrategyType {}

export class RollingPushStrategy implements IMigrationStrategyType {
  static get type(): MigrationStrategyType {
    return 'rollingPush';
  }

  public concurrentRelaunches = 1;
  public concurrentRelaunchesAsPercentage = true;

  public waitTime = 0;
}

export class PipelineStrategy implements IMigrationStrategyType {
  static get type(): MigrationStrategyType {
    return 'pipeline';
  }

  public application: string;
  public pipelineId: string;
  public parameters: IPipelineParam[];
}

/**
 * Actual stored parameter in PipelineStrategy
 */
export interface IPipelineParam {
  name: string;
  value: any;
}

/**
 * Migration strategy default config and for overrides
 */
export interface IMigrationStrategy {
  type: MigrationStrategyType;
  config: RollingPushStrategy | PipelineStrategy;
}

/**
 * Override configuration for a cluster
 */
export interface IOverride {
  account: string;
  region: string;
  stack: string;
  detail: string;
  strategy: IMigrationStrategy;
}

/**
 * Actual config object
 */
export interface ITitusMigrationConfig {
  defaultStrategy: IMigrationStrategy;
  overrides: IOverride[];
}

export class TitusMigrationConfigController implements ng.IComponentController {
  public application: Application;
  public config: ITitusMigrationConfig;
  public viewState: ITitusMigrationViewState = {
    originalConfig: null,
    originalStringVal: null,
    saving: false,
    saveError: false,
    isDirty: false,
    accountsLoaded: false,
  };
  public accounts: any[];
  public regionsByAccount: any = {};

  public $onInit(): void {
    this.config = this.application.attributes.titusTaskMigration || {
      defaultStrategy: { type: RollingPushStrategy.type, config: new RollingPushStrategy() },
      overrides: [],
    };
    this.viewState.originalConfig = cloneDeep(this.config);
    this.viewState.originalStringVal = toJson(this.viewState.originalConfig);

    this.initializeOptions();
  }

  private initializeOptions(): void {
    AccountService.getAllAccountDetailsForProvider('titus').then((accounts: any[]) => {
      this.accounts = accounts.map((account: any) => account.name);
      accounts.forEach((account: any) => {
        this.regionsByAccount[account.name] = ['*'].concat(account.regions.map((r: any) => r.name));
      });
      this.viewState.accountsLoaded = true;
    });
  }

  public addOverride(): void {
    this.config.overrides.push({
      account: this.accounts[0],
      region: this.regionsByAccount[this.accounts[0]][0],
      stack: null,
      detail: null,
      strategy: { type: RollingPushStrategy.type, config: new RollingPushStrategy() },
    });
    this.configChanged();
  }

  public removeOverride(index: number): void {
    this.config.overrides.splice(index, 1);
    this.configChanged();
  }

  public configChanged(): void {
    this.viewState.isDirty = this.viewState.originalStringVal !== toJson(this.config);
  }
}

const titusMigrationConfigComponent: ng.IComponentOptions = {
  bindings: {
    application: '=',
  },
  controller: TitusMigrationConfigController,
  templateUrl: require('./titusMigrationConfig.component.html')
};

export const TITUS_MIGRATION_CONFIG_COMPONENT = 'spinnaker.titus.migration.config.component';

module(TITUS_MIGRATION_CONFIG_COMPONENT, [TITUS_MIGRATION_CONFIGURER_COMPONENT, CONFIG_SECTION_FOOTER]).component(
  'titusMigrationConfig',
  titusMigrationConfigComponent,
);
