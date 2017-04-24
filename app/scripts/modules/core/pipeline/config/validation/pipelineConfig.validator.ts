import {module} from 'angular';
import {Subject} from 'rxjs';

import {IExecutionStageSummary} from 'core/domain/index';
import {IStage} from 'core/domain/IStage';
import {IPipeline} from 'core/domain/IPipeline';
import {ITrigger} from 'core/domain/ITrigger';
import {PIPELINE_CONFIG_PROVIDER} from 'core/pipeline/config/pipelineConfigProvider';

export interface IStageValidationResults {
  stage: IStage;
  messages: string[];
}

export interface IPipelineValidationResults {
  stages: IStageValidationResults[];
  pipeline: string[];
  hasWarnings?: boolean;
  preventSave?: boolean;
}

export interface IValidatorConfig {
  type: string;
  message?: string;
  skipValidation?: (pipeline: IPipeline, stage: IStage) => boolean;
  preventSave?: boolean;
  fieldName?: string;
  fieldLabel?: string;
  checkParentTriggers?: boolean;
}

export interface IStageOrTriggerTypeConfig {
  label: string;
  description: string;
  key: string;
  templateUrl: string;
  controller: string;
  controllerAs: string;
  validators: IValidatorConfig[];
}

export interface ITriggerTypeConfig extends IStageOrTriggerTypeConfig {
  manualExecutionHandler?: string;
}

export interface IStageTypeConfig extends IStageOrTriggerTypeConfig {
  executionDetailsUrl: string;
  executionStepLabelUrl?: string;
  defaultTimeoutMs?: number;
  provides?: string;
  providesFor?: string[];
  cloudProvider?: string;
  cloudProviders?: string[];
  alias?: string;
  useBaseProvider?: boolean;
  executionLabelComponent?: React.Component<{ stage: IExecutionStageSummary }, any>;
  accountExtractor?: (stage: IStage) => string;
  extraLabelLines?: (stage: IStage) => number;
  restartable?: boolean;
  synthetic?: boolean;
  nameToCheckInTest?: string;
}

export interface IStageOrTriggerValidator {
  validate(pipeline: IPipeline,
           stageOrTrigger: IStage | ITrigger,
           validator: IValidatorConfig,
           config: IStageOrTriggerTypeConfig): string | ng.IPromise<string>;
}

export interface ICustomValidator extends IStageOrTriggerValidator, IValidatorConfig {
  [k: string]: any;
}

export class PipelineConfigValidator implements ng.IServiceProvider {

  private validators: Map<string, IStageOrTriggerValidator> = new Map();
  private validationStream: Subject<IPipelineValidationResults> = new Subject();

  public registerValidator(type: string, validator: IStageOrTriggerValidator) {
    this.validators.set(type, validator);
  }

  static get $inject() { return ['$log', '$q', 'pipelineConfig']; }

  constructor(private $log: ng.ILogService,
              private $q: ng.IQService,
              private pipelineConfig: any) {}

  public validatePipeline(pipeline: IPipeline): ng.IPromise<IPipelineValidationResults> {
    const stages: IStage[] = pipeline.stages || [],
          triggers: ITrigger[] = pipeline.triggers || [],
          validations: ng.IPromise<string>[] = [],
          pipelineValidations: string[] = this.getPipelineLevelValidations(pipeline),
          stageValidations: Map<IStage, string[]> = new Map();
    let preventSave = false;

    triggers.forEach((trigger, index) => {
      const config: ITriggerTypeConfig = this.pipelineConfig.getTriggerConfig(trigger.type);
      if (config && config.validators) {
        config.validators.forEach((validator) => {
          const typedValidator = this.getValidator(validator);
          if (!typedValidator) {
            this.$log.warn(`No validator of type "${validator.type}" found, ignoring validation on trigger "${(index + 1)}" (${trigger.type})`);
          } else {
            validations.push(
              this.$q.resolve<string>(typedValidator.validate(pipeline, trigger, validator, config))
                .then(message => {
                  if (message && !pipelineValidations.includes(message)) {
                    pipelineValidations.push(message);
                    if (validator.preventSave) {
                      preventSave = true;
                    }
                  }
                  return message;
                })
            );
          }
        });
      }
    });
    stages.forEach((stage) => {
      const config: IStageTypeConfig = this.pipelineConfig.getStageConfig(stage);
      if (config && config.validators) {
        config.validators.forEach((validator) => {
          if (validator.skipValidation && validator.skipValidation(pipeline, stage)) {
            return;
          }
          const typedValidator = this.getValidator(validator);
          if (!typedValidator) {
            this.$log.warn(`No validator of type "${validator.type}" found, ignoring validation on stage "${stage.name}" (${stage.type})`);
          } else {
            validations.push(
              this.$q.resolve<string>(typedValidator.validate(pipeline, stage, validator, config)).then((message: string) => {
                if (message) {
                  if (!stageValidations.has(stage)) {
                    stageValidations.set(stage, [] as string[]);
                  }
                  if (!stageValidations.get(stage).includes(message)) {
                    stageValidations.get(stage).push(message);
                    if (validator.preventSave) {
                      preventSave = true;
                    }
                  }
                }
                return message;
              })
            );
          }
        });
      }
    });

    return this.$q.all(validations).then(() => {
      const results = {
        stages: Array.from(stageValidations).map(([stage, messages]) => ({ stage, messages })),
        pipeline: pipelineValidations,
        hasWarnings: false,
        preventSave,
      };
      results.hasWarnings = results.pipeline.length > 0 || results.stages.length > 0;
      this.validationStream.next(results);
      return results;
    });
  }

  private getValidator(validator: IValidatorConfig): IStageOrTriggerValidator {
    return validator.type === 'custom' ? validator as ICustomValidator : this.validators.get(validator.type);
  }

  private getPipelineLevelValidations(pipeline: IPipeline): string[] {
    const messages: string[] = [];
    if ((pipeline.parameterConfig || []).some(p => !p.name)) {
      messages.push('<b>Name</b> is a required field for parameters.');
    }
    if (pipeline.strategy && !(pipeline.stages.some(stage => stage.type === 'deploy'))) {
      messages.push('To be able to create new server groups, a custom strategy should contain a Deploy stage.');
    }
    return messages;
  }

  /**
   * Subscribes to validation events
   * @param method
   * @returns {Subscription}, which should be unsubscribed when the subscriber is destroyed
   */
  public subscribe(method: (result: IPipelineValidationResults) => any) {
    return this.validationStream.subscribe(method);
  }

  public $get() {
    return this;
  }

}

export const PIPELINE_CONFIG_VALIDATOR = 'spinnaker.core.pipeline.config.validator';
module(PIPELINE_CONFIG_VALIDATOR, [
  PIPELINE_CONFIG_PROVIDER,
]).service('pipelineConfigValidator', PipelineConfigValidator)
  .run((pipelineConfigValidator: PipelineConfigValidator) => {
    // placeholder - custom validators must implement the ICustomValidator interface
    pipelineConfigValidator.registerValidator('custom', null);
  });
