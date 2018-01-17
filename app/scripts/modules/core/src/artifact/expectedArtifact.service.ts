import { copy, module } from 'angular';

import { IArtifact, IExpectedArtifact, IPipeline, IStage } from 'core/domain';
import { PipelineConfigService } from 'core';

export class ExpectedArtifactService {
  constructor(private pipelineConfigService: PipelineConfigService) {
    'ngInject';
  }

  public getExpectedArtifactsAvailableToStage(stage: IStage, pipeline: IPipeline): IExpectedArtifact[] {
    let result = pipeline.expectedArtifacts || [];
    this.pipelineConfigService.getAllUpstreamDependencies(pipeline, stage)
      .forEach(s => {
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
}

export function summarizeExpectedArtifact() {
  return function (expected: IExpectedArtifact): string {
    if (!expected) {
      return '';
    }

    const artifact = copy(expected.matchArtifact);
    return Object.keys(artifact)
      .filter((k: keyof IArtifact) => artifact[k])
      .filter((k) => k !== 'kind')
      .map((k: keyof IArtifact) => (`${k}: ${artifact[k]}`))
      .join(', ');
  }
}

export const EXPECTED_ARTIFACT_SERVICE = 'spinnaker.core.artifacts.expected.service';
module(EXPECTED_ARTIFACT_SERVICE , [])
  .filter('summarizeExpectedArtifact', summarizeExpectedArtifact)
  .service('expectedArtifactService', ExpectedArtifactService);


