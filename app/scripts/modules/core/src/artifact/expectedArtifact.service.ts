import { PipelineConfigService } from 'core/pipeline/config/services/PipelineConfigService';
import { IPipeline, IStage, IExpectedArtifact } from 'core/domain';

export class ExpectedArtifactService {
  public static getExpectedArtifactsAvailableToStage(stage: IStage, pipeline: IPipeline): IExpectedArtifact[] {
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
}
