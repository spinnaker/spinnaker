import { PipelineConfigService } from 'core/pipeline/config/services/PipelineConfigService';
import { IPipeline, IStage, IExpectedArtifact, IExecutionContext } from 'core/domain';
import { UUIDGenerator } from 'core/utils/uuid.service';

export class ExpectedArtifactService {
  public static getExpectedArtifactsAvailableToStage(stage: IStage, pipeline: IPipeline): IExpectedArtifact[] {
    if (!stage || !pipeline) {
      return [];
    }
    let result = pipeline.expectedArtifacts || [];
    PipelineConfigService.getAllUpstreamDependencies(pipeline, stage).forEach(s => {
      const expectedArtifact = (s as any).expectedArtifact;
      if (expectedArtifact) {
        result = result.concat(expectedArtifact);
      }

      const expectedArtifacts = (s as any).expectedArtifacts;
      if (expectedArtifacts) {
        result = result.concat(expectedArtifacts);
      }
    });
    return result;
  }

  public static accumulateArtifacts<T>(fields: string[]): (stageContext: IExecutionContext) => T[] {
    // The value of each field will be either a string with a single artifact id, or an array of artifact ids
    // In either case, concatenate the value(s) onto the array of artifacts; the one exception
    // is that we don't want to include an empty string in the artifact list, so omit any falsey values.
    return (stageContext: IExecutionContext) =>
      fields
        .map(field => stageContext[field])
        .filter(v => v)
        .reduce((array, value) => array.concat(value), []);
  }

  public static createEmptyArtifact(kind: string): IExpectedArtifact {
    return {
      id: UUIDGenerator.generateUuid(),
      usePriorArtifact: false,
      useDefaultArtifact: false,
      matchArtifact: {
        id: UUIDGenerator.generateUuid(),
        kind,
      },
      defaultArtifact: {
        id: UUIDGenerator.generateUuid(),
        kind,
      },
    };
  }

  public static addNewArtifactTo(obj: any): IExpectedArtifact {
    const artifact = ExpectedArtifactService.createEmptyArtifact('custom');
    if (obj.expectedArtifacts == null) {
      obj.expectedArtifacts = [];
    }
    obj.expectedArtifacts.push(artifact);
    return artifact;
  }
}
