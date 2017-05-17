import {module} from 'angular';

import {
  PIPELINE_CONFIG_SERVICE,
  PipelineConfigService
} from '../services/pipelineConfig.service';
import {
  IStageOrTriggerValidator, IValidatorConfig,
  PipelineConfigValidator, PIPELINE_CONFIG_VALIDATOR
} from './pipelineConfig.validator';
import {NAMING_SERVICE, NamingService} from 'core/naming/naming.service';
import {IPipeline, IStage, IStageOrTriggerTypeConfig} from 'core/domain';

export interface ITargetImpedanceValidationConfig extends IValidatorConfig {
  stageTypes?: string[];
  stageType?: string;
  message: string;
}

export class TargetImpedanceValidator implements IStageOrTriggerValidator {
  constructor(private pipelineConfigService: PipelineConfigService, private namingService: NamingService) { 'ngInject'; }

  public validate(pipeline: IPipeline,
                  stage: IStage,
                  validator: ITargetImpedanceValidationConfig,
                  _config: IStageOrTriggerTypeConfig): string {

    const stagesToTest: IStage[] = this.pipelineConfigService.getAllUpstreamDependencies(pipeline, stage),
          regions: string[] = stage['regions'] || [];
    let allRegionsFound = true;

    regions.forEach((region) => {
      let regionFound = false;
      stagesToTest.forEach((toTest) => {
        if (toTest.type === 'deploy' && toTest['clusters'] && toTest['clusters'].length) {
          toTest['clusters'].forEach((cluster: any) => {
            const clusterName: string = this.namingService.getClusterName(cluster.application, cluster.stack, cluster.freeFormDetails);
            if (clusterName === stage['cluster'] && cluster.account === stage['credentials'] && cluster.availabilityZones && cluster.availabilityZones.hasOwnProperty(region)) {
              regionFound = true;
            }
          });
        } else if (toTest.type === 'cloneServerGroup' && toTest['targetCluster'] === stage['cluster'] && toTest['region'] === region) {
          regionFound = true;
        }
      });
      if (!regionFound) {
        allRegionsFound = false;
      }
    });
    if (!allRegionsFound) {
      return validator.message;
    }
    return null;
  }
}

export const TARGET_IMPEDANCE_VALIDATOR = 'spinnaker.core.pipeline.validation.config.targetImpedance';
module(TARGET_IMPEDANCE_VALIDATOR, [
  PIPELINE_CONFIG_SERVICE,
  NAMING_SERVICE,
  PIPELINE_CONFIG_VALIDATOR,
]).service('targetImpedanceValidator', TargetImpedanceValidator)
  .run((pipelineConfigValidator: PipelineConfigValidator, targetImpedanceValidator: TargetImpedanceValidator) => {
    pipelineConfigValidator.registerValidator('targetImpedance', targetImpedanceValidator);
  });
