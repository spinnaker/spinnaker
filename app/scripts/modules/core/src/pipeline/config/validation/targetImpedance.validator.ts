import { IStageOrTriggerValidator, IValidatorConfig, PipelineConfigValidator } from './PipelineConfigValidator';
import { IPipeline, IStage, IStageOrTriggerTypeConfig } from '../../../domain';
import { NameUtils } from '../../../naming';
import { PipelineConfigService } from '../services/PipelineConfigService';

export interface ITargetImpedanceValidationConfig extends IValidatorConfig {
  stageTypes?: string[];
  stageType?: string;
  message: string;
}

export class TargetImpedanceValidator implements IStageOrTriggerValidator {
  public validate(
    pipeline: IPipeline,
    stage: IStage,
    validator: ITargetImpedanceValidationConfig,
    _config: IStageOrTriggerTypeConfig,
  ): string {
    const stagesToTest: IStage[] = PipelineConfigService.getAllUpstreamDependencies(pipeline, stage);
    const regions: string[] = stage['regions'] || [];
    let allRegionsFound = true;

    regions.forEach((region) => {
      let regionFound = false;
      stagesToTest.forEach((toTest) => {
        if (toTest.type === 'deploy' && toTest['clusters'] && toTest['clusters'].length) {
          toTest['clusters'].forEach((cluster: any) => {
            const clusterName: string = NameUtils.getClusterName(
              cluster.application,
              cluster.stack,
              cluster.freeFormDetails,
            );
            if (
              clusterName === stage['cluster'] &&
              cluster.account === stage['credentials'] &&
              cluster.availabilityZones &&
              cluster.availabilityZones.hasOwnProperty(region)
            ) {
              regionFound = true;
            }
          });
        } else if (
          toTest.type === 'cloneServerGroup' &&
          NameUtils.getClusterName(toTest['application'], toTest['stack'], toTest['freeFormDetails']) ===
            stage['cluster'] &&
          toTest['region'] === region
        ) {
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

PipelineConfigValidator.registerValidator('targetImpedance', new TargetImpedanceValidator());
