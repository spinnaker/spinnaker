import { PipelineConfigService } from 'core/pipeline/config/services/PipelineConfigService';
import { Registry, IPipeline, IStage, IExpectedArtifact, IExecutionContext, IArtifact, IArtifactSource } from 'core';
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

  public static addArtifactTo(artifact: IExpectedArtifact, obj: any): IExpectedArtifact {
    if (obj.expectedArtifacts == null) {
      obj.expectedArtifacts = [];
    }
    obj.expectedArtifacts.push(artifact);
    return artifact;
  }

  public static addNewArtifactTo(obj: any): IExpectedArtifact {
    return ExpectedArtifactService.addArtifactTo(ExpectedArtifactService.createEmptyArtifact('custom'), obj);
  }

  public static artifactFromExpected(expected: IExpectedArtifact): IArtifact | null {
    if (expected && expected.matchArtifact) {
      return expected.matchArtifact;
    } else {
      return null;
    }
  }

  public static sourcesForPipelineStage(
    pipelineGetter: () => IPipeline,
    stage: IStage,
  ): Array<IArtifactSource<IPipeline | IStage>> {
    type ArtifactSource = IArtifactSource<IPipeline | IStage>;
    const sources: ArtifactSource[] = [
      {
        get source() {
          return pipelineGetter();
        },
        label: 'Pipeline Trigger',
      },
    ];
    PipelineConfigService.getAllUpstreamDependencies(pipelineGetter(), stage)
      .filter(s => Registry.pipeline.getStageConfig(s).producesArtifacts)
      .map(s => ({ source: s, label: 'Stage (' + s.name + ')' }))
      .forEach(s => sources.push(s));
    return sources;
  }

  public static getKind(artifact: IArtifact): string {
    if (artifact != null) {
      if (artifact.kind) {
        return artifact.kind;
      } else {
        const artifactType = artifact.type;
        const inferredKindConfig = Registry.pipeline.getArtifactKinds().find(k => {
          return k.type === artifactType;
        });
        if (inferredKindConfig != null) {
          return inferredKindConfig.key;
        }
      }
    }
    return null;
  }
}
