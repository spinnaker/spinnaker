import { hri as HumanReadableIds } from 'human-readable-ids';

import { IPipeline, IPipelineTemplateConfigV2, IPipelineTemplateV2 } from 'core/domain';
import { PipelineJSONService } from 'core/pipeline/config/services/pipelineJSON.service';
import { UUIDGenerator } from 'core/utils';

export class PipelineTemplateV2Service {
  public static createPipelineTemplate(pipeline: IPipeline, owner: string): IPipelineTemplateV2 {
    return {
      id: UUIDGenerator.generateUuid(),
      metadata: {
        description: `A pipeline template derived from pipeline "${pipeline.name}" in application "${
          pipeline.application
        }"`,
        name: HumanReadableIds.random(),
        owner,
        scopes: ['global'],
      },
      pipeline: PipelineJSONService.clone(pipeline),
      protect: false,
      schema: PipelineTemplateV2Service.schema,
      variables: [],
    };
  }

  public static isV2PipelineConfig(pipelineConfig: IPipeline): boolean {
    return pipelineConfig.schema === PipelineTemplateV2Service.schema;
  }

  public static getUnsupportedCopy(task: string): string {
    return `${task} of templated v2 pipelines through the UI is unsupported. Use Spin CLI instead.`;
  }

  public static idForTemplate(template: { id: string; digest?: string }): string {
    const { id, digest = '' } = template;
    return `${id}:${digest}`;
  }

  public static getPipelineTemplateConfigV2(id: string): Partial<IPipelineTemplateConfigV2> {
    // Scoped to Front50 in the short-term.
    return {
      schema: PipelineTemplateV2Service.schema,
      template: {
        artifactAccount: 'front50ArtifactCredentials',
        reference: `spinnaker://${id}`,
        type: 'front50/pipelineTemplate',
      },
    };
  }

  private static schema = 'v2';
}
