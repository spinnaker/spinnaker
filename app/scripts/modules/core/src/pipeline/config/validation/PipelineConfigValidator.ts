import { FormikErrors } from 'formik';
import { flatten, isNumber, values } from 'lodash';
import { $log, $q } from 'ngimport';
import { Subject, Subscription } from 'rxjs';

import {
  IPipeline,
  IStage,
  IStageOrTriggerTypeConfig,
  IStageTypeConfig,
  ITrigger,
  ITriggerTypeConfig,
} from '../../../domain';
import { Registry } from '../../../registry';

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

export interface IValidatorField {
  fieldName?: string;
  fieldLabel?: string;
}

export interface IValidatorConfig extends IValidatorField {
  type: string;
  message?: string;
  skipValidation?: (pipeline: IPipeline, stage: IStage) => boolean;
  preventSave?: boolean;
  checkParentTriggers?: boolean;
  fields?: IValidatorField[];
}

export interface IStageOrTriggerValidator {
  validate(
    pipeline: IPipeline,
    stageOrTrigger: IStage | ITrigger,
    validator: IValidatorConfig,
    config: IStageOrTriggerTypeConfig,
  ): string | PromiseLike<string>;
}

export interface ICustomValidator extends IStageOrTriggerValidator, IValidatorConfig {
  [k: string]: any;
}

export class PipelineConfigValidator {
  private static validators: Map<string, IStageOrTriggerValidator> = new Map();
  private static validationStream: Subject<IPipelineValidationResults> = new Subject();

  public static registerValidator(type: string, validator: IStageOrTriggerValidator) {
    this.validators.set(type, validator);
  }

  public static validatePipeline(pipeline: IPipeline): PromiseLike<IPipelineValidationResults> {
    const stages: IStage[] = pipeline.stages || [];
    const triggers: ITrigger[] = pipeline.triggers || [];
    const validations: Array<PromiseLike<void>> = [];
    const pipelineValidations: string[] = this.getPipelineLevelValidations(pipeline);
    const stageValidations: Map<IStage, string[]> = new Map();
    let preventSave = false;

    triggers.forEach((trigger, index) => {
      const config: ITriggerTypeConfig = Registry.pipeline.getTriggerConfig(trigger.type);
      if (config && config.validators) {
        config.validators.forEach((validator) => {
          const typedValidator = this.getValidator(validator);
          if (!typedValidator) {
            $log.warn(
              `No validator of type "${validator.type}" found, ignoring validation on trigger "${index + 1}" (${
                trigger.type
              })`,
            );
          } else {
            validations.push(
              $q.resolve<string>(typedValidator.validate(pipeline, trigger, validator, config)).then((message) => {
                if (message && !pipelineValidations.includes(message)) {
                  pipelineValidations.push(message);
                  if (validator.preventSave) {
                    preventSave = true;
                  }
                }
              }),
            );
          }
        });
      } else if (config && config.validateFn) {
        validations.push(
          $q<FormikErrors<IStage>>((resolve, reject) =>
            Promise.resolve(config.validateFn(trigger, { pipeline })).then(resolve, reject),
          ).then((errors: FormikErrors<ITrigger>) => {
            PipelineConfigValidator.flattenValues(errors).forEach((message) => {
              pipelineValidations.push(message);
            });
          }),
        );
      }
    });
    stages.forEach((stage) => {
      const config: IStageTypeConfig = Registry.pipeline.getStageConfig(stage);
      if (config && config.validators) {
        config.validators.forEach((validator) => {
          if (validator.skipValidation && validator.skipValidation(pipeline, stage)) {
            return;
          }
          const typedValidator = this.getValidator(validator);
          if (!typedValidator) {
            $log.warn(
              `No validator of type "${validator.type}" found, ignoring validation on stage "${stage.name}" (${stage.type})`,
            );
          } else {
            validations.push(
              $q
                .resolve<string>(typedValidator.validate(pipeline, stage, validator, config))
                .then((message: string) => {
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
                }),
            );
          }
        });
      } else if (config && config.validateFn) {
        validations.push(
          $q<FormikErrors<IStage>>((resolve, reject) =>
            Promise.resolve(config.validateFn(stage, { pipeline })).then(resolve, reject),
          ).then((errors: FormikErrors<IStage>) => {
            const array: string[] = PipelineConfigValidator.flattenValues(errors);
            if (array && array.length > 0) {
              stageValidations.set(stage, array);
            }
          }),
        );
      }

      if (stage.stageTimeoutMs !== undefined && !(isNumber(stage.stageTimeoutMs) && stage.stageTimeoutMs > 0)) {
        stageValidations.set(stage, [
          ...(stageValidations.get(stage) || []),
          'Stage is configured to fail after a specific amount of time, but no time is set.',
        ]);
      }
    });

    return $q.all(validations).then(() => {
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

  private static flattenValues = (maybeObj: string | object): string[] => {
    if (typeof maybeObj === 'string') {
      return [maybeObj];
    }
    return flatten(values(maybeObj).map(PipelineConfigValidator.flattenValues)) as string[];
  };

  private static getValidator(validator: IValidatorConfig): IStageOrTriggerValidator {
    return validator.type === 'custom' ? (validator as ICustomValidator) : this.validators.get(validator.type);
  }

  private static getPipelineLevelValidations(pipeline: IPipeline): string[] {
    const messages: string[] = [];
    if ((pipeline.parameterConfig || []).some((p) => !p.name)) {
      messages.push('<b>Name</b> is a required field for parameters.');
    }
    if (pipeline.strategy && !pipeline.stages.some((stage) => stage.type === 'deploy')) {
      messages.push('To be able to create new server groups, a custom strategy should contain a Deploy stage.');
    }
    if ((pipeline.expectedArtifacts || []).some((a) => !a.matchArtifact || (a.matchArtifact as any) === {})) {
      messages.push('Every expected artifact must specify an artifact to match against.');
    }
    return messages;
  }

  /**
   * Subscribes to validation events
   * @param method
   * @returns {Subscription}, which should be unsubscribed when the subscriber is destroyed
   */
  public static subscribe(method: (result: IPipelineValidationResults) => any): Subscription {
    return this.validationStream.subscribe(method);
  }
}

// placeholder - custom validators must implement the ICustomValidator interface
PipelineConfigValidator.registerValidator('custom', null);
