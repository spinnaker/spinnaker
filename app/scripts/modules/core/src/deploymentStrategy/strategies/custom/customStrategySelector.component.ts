import { IComponentController, IComponentOptions, module } from 'angular';

import { APPLICATION_READ_SERVICE, ApplicationReader } from 'core/application/service/application.read.service';
import { PIPELINE_CONFIG_SERVICE, PipelineConfigService } from 'core/pipeline/config/services/pipelineConfig.service';
import { IParameter, IPipeline } from 'core/domain';

interface ICustomStrategyState {
  pipelinesLoaded: boolean;
  applications: string[];
  pipelines: IPipeline[];
  pipelineParameters?: IParameter[];
  useDefaultParameters: { [key: string]: boolean };
  userSuppliedParameters: { [key: string]: any };
  currentApplicationCount: number;
}

interface ICustomStrategyCommand {
  application?: string;
  strategyApplication?: string;
  strategyPipeline?: string;
  pipelineParameters?: { [key: string]: any };
}

class CustomStrategySelectorController implements IComponentController {

  public command: ICustomStrategyCommand;
  public state: ICustomStrategyState = {
    pipelinesLoaded: false,
    applications: [],
    pipelines: [],
    pipelineParameters: [],
    useDefaultParameters: {},
    userSuppliedParameters: {},
    currentApplicationCount: 20,
  };

  constructor(private applicationReader: ApplicationReader, private pipelineConfigService: PipelineConfigService) {
    'ngInject';
  }

  public $onInit() {
    if (!this.command.strategyApplication) {
      this.command.strategyApplication = this.command.application;
    }
    this.applicationReader.listApplications().then((applications) => {
      this.state.applications = applications.map(a => a.name).sort();
      this.initializeStrategies();
    });
  }

  public addMoreApplications(): void {
    this.state.currentApplicationCount += 20;
  }

  public initializeStrategies(): void {
    if (this.command.application) {
      this.pipelineConfigService.getStrategiesForApplication(this.command.strategyApplication).then((pipelines) => {
        this.state.pipelines = pipelines;
        if (pipelines.every(p => p.id !== this.command.strategyPipeline)) {
          this.command.strategyPipeline = null;
        }
        this.state.pipelinesLoaded = true;
        this.updatePipelineConfig();
      });
    }
  }

  public updatePipelineConfig(): void {
    if (this.command && this.command.strategyApplication && this.command.strategyPipeline) {
      const config = this.state.pipelines.find(p => p.id === this.command.strategyPipeline);
      if (config && config.parameterConfig) {
        if (!this.command.pipelineParameters) {
          this.command.pipelineParameters = {};
        }
        this.state.pipelineParameters = config.parameterConfig;
        this.state.userSuppliedParameters = this.command.pipelineParameters;
        this.state.useDefaultParameters = {};
        this.configureParamDefaults();
      } else {
        this.clearParams();
      }
    } else {
      this.clearParams();
    }
  }

  public updateParam(parameter: string): void {
    if (this.state.useDefaultParameters[parameter] === true) {
      delete this.state.userSuppliedParameters[parameter];
      delete this.command.pipelineParameters[parameter];
    } else if (this.state.userSuppliedParameters[parameter]) {
      this.command.pipelineParameters[parameter] = this.state.userSuppliedParameters[parameter];
    }
  }

  private configureParamDefaults(): void {
    this.state.pipelineParameters.forEach((param: any) => {
      const defaultValue = param.default;
      if (defaultValue !== null && defaultValue !== undefined) {
        const configuredParamValue = this.command.pipelineParameters[param.name];
        if (configuredParamValue === undefined || configuredParamValue === defaultValue) {
          this.state.useDefaultParameters[param.name] = true;
          this.command.pipelineParameters[param.name] = defaultValue;
        }
      }
    });
  }

  private clearParams(): void {
    this.state.pipelineParameters = [];
    this.state.useDefaultParameters = {};
    this.state.userSuppliedParameters = {};
  }
}

const customStrategyComponent: IComponentOptions = {
  bindings: {
    command: '=',
  },
  templateUrl: require('./customStrategySelector.component.html'),
  controller: CustomStrategySelectorController,
};

export const CUSTOM_STRATEGY_SELECTOR_COMPONENT = 'spinnaker.core.deploymentStrategy.custom.customStrategySelector';
module(CUSTOM_STRATEGY_SELECTOR_COMPONENT, [
  APPLICATION_READ_SERVICE,
  PIPELINE_CONFIG_SERVICE,
]).component('customStrategySelector', customStrategyComponent);
