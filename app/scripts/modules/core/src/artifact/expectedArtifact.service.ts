import { hri as HumanReadableIds } from 'human-readable-ids';
import { get } from 'lodash';

import {
  IArtifact,
  IArtifactKindConfig,
  IArtifactSource,
  IExecutionContext,
  IExpectedArtifact,
  IPipeline,
  IStage,
} from '../domain';
import { PipelineConfigService } from '../pipeline';
import { Registry } from '../registry';
import { UUIDGenerator } from '../utils';

export class ExpectedArtifactService {
  public static getExpectedArtifactsAvailableToStage(stage: IStage, pipeline: IPipeline): IExpectedArtifact[] {
    if (!stage || !pipeline) {
      return [];
    }
    let result = pipeline.expectedArtifacts || [];
    PipelineConfigService.getAllUpstreamDependencies(pipeline, stage).forEach((s) => {
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
        .map((field): string => get(stageContext, field))
        .filter((v) => v)
        .reduce((array, value) => array.concat(value), []);
  }

  public static createEmptyArtifact(): IExpectedArtifact {
    return {
      id: UUIDGenerator.generateUuid(),
      usePriorArtifact: false,
      useDefaultArtifact: false,
      matchArtifact: {
        id: UUIDGenerator.generateUuid(),
        customKind: true,
      },
      defaultArtifact: {
        id: UUIDGenerator.generateUuid(),
        customKind: true,
      },
      displayName: HumanReadableIds.random(),
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
    return ExpectedArtifactService.addArtifactTo(ExpectedArtifactService.createEmptyArtifact(), obj);
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
      .filter((s) => Registry.pipeline.getStageConfig(s).producesArtifacts)
      .map((s) => ({ source: s, label: 'Stage (' + s.name + ')' }))
      .forEach((s) => sources.push(s));
    return sources;
  }

  public static getKindConfig(artifact: IArtifact, isDefault: boolean): IArtifactKindConfig {
    if (artifact == null || artifact.customKind || artifact.kind === 'custom') {
      return Registry.pipeline.getCustomArtifactKind();
    }
    const kinds = isDefault ? Registry.pipeline.getDefaultArtifactKinds() : Registry.pipeline.getMatchArtifactKinds();
    const inferredKindConfig = kinds.find((k) => k.type === artifact.type);
    if (inferredKindConfig !== undefined) {
      return inferredKindConfig;
    }
    return Registry.pipeline.getCustomArtifactKind();
  }
}
