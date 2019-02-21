import { module } from 'angular';

import { Application, ApplicationReader, PipelineConfigService } from '@spinnaker/core';

import {
  IMigrationStrategy,
  IMigrationStrategyType,
  PipelineStrategy,
  RollingPushStrategy,
} from './titusMigrationConfig.component';

interface IPipelineOption {
  name: string;
  id: string;
  parameters: IPipelineParamConfig[];
}

interface IPipelineParamConfig {
  name: string;
  defaultValue: string;
  options: string[];
}

class TitusMigrationConfigurerController implements ng.IComponentController {
  public config: IMigrationStrategy;
  public application: Application;
  public configChanged: () => void;
  public applications: any[];
  public pipelineOptions: IPipelineOption[];
  public pipelinesLoaded = false;

  public migrationOptions: string[] = [RollingPushStrategy.type, PipelineStrategy.type];

  public pipelineSelected(config: PipelineStrategy): void {
    const selectedPipeline = this.pipelineOptions.find(o => o.id === config.pipelineId);
    if (selectedPipeline && selectedPipeline.parameters) {
      config.parameters = selectedPipeline.parameters.map((o: IPipelineParamConfig) => {
        return {
          name: o.name,
          value: o.defaultValue,
        };
      });
    } else {
      config.parameters = null;
    }
    this.configChanged();
  }

  public getParameterOptions(paramName: string): string[] {
    const config: IMigrationStrategyType = this.config.config;
    if (config instanceof PipelineStrategy) {
      const pipelineOption = this.pipelineOptions.find(o => o.id === config.pipelineId);
      if (pipelineOption && pipelineOption.parameters) {
        const paramConfig = pipelineOption.parameters.find(o => o.name === paramName);
        return paramConfig ? paramConfig.options : null;
      }
    }
    return null;
  }

  public updateType(): void {
    if (this.config.type === RollingPushStrategy.type) {
      this.config.config = new RollingPushStrategy();
    }
    if (this.config.type === PipelineStrategy.type) {
      const newConfig = new PipelineStrategy();
      newConfig.application = this.application.name;
      this.config.config = newConfig;
      this.applicationSelected(newConfig);
    }
    this.configChanged();
  }

  public applicationSelected(config: PipelineStrategy): void {
    this.pipelinesLoaded = false;
    PipelineConfigService.getPipelinesForApplication(config.application).then((pipelineConfigs: any[]) => {
      this.pipelinesLoaded = true;
      this.pipelineOptions = [];
      pipelineConfigs.forEach((pipelineConfig: any) => {
        this.getPipelineOptions(pipelineConfig);
        this.pipelineOptions.push({
          name: pipelineConfig.name,
          id: pipelineConfig.id,
          parameters: this.getPipelineOptions(pipelineConfig),
        });
      });
      this.configChanged();
    });
  }

  private getPipelineOptions(pipelineConfig: any): IPipelineParamConfig[] {
    const options: IPipelineParamConfig[] = [];
    if (pipelineConfig.parameterConfig && pipelineConfig.parameterConfig.length) {
      pipelineConfig.parameterConfig.forEach((parameter: any) => {
        options.push({
          name: parameter.name,
          defaultValue: parameter.default,
          options: parameter.options ? parameter.options.map((o: any) => o.value) : null,
        });
      });
    }
    return options;
  }

  public $onInit(): void {
    let config: RollingPushStrategy | PipelineStrategy = new RollingPushStrategy();
    if (this.config.type === PipelineStrategy.type) {
      config = new PipelineStrategy();
    }
    ApplicationReader.listApplications().then((applications: any[]) => {
      this.applications = applications;
      if (config instanceof PipelineStrategy) {
        this.applicationSelected(config);
      }
    });

    Object.assign(config, this.config.config);
    this.config.config = config;
  }
}

const titusMigrationConfigurerComponent: ng.IComponentOptions = {
  bindings: {
    application: '<',
    config: '<',
    configChanged: '&',
  },
  controller: TitusMigrationConfigurerController,
  templateUrl: require('./titusMigrationConfigurer.component.html')
};

export const TITUS_MIGRATION_CONFIGURER_COMPONENT = 'spinnaker.titus.migration.configurer.component';

module(TITUS_MIGRATION_CONFIGURER_COMPONENT, []).component(
  'titusMigrationConfigurer',
  titusMigrationConfigurerComponent,
);
