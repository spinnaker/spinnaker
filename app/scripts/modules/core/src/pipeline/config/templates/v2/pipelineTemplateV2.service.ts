import { hri as HumanReadableIds } from 'human-readable-ids';

import { IPipeline, IPipelineTemplateConfigV2, IPipelineTemplateV2 } from '../../../../domain';
import { PipelineJSONService } from '../../services/pipelineJSON.service';
import { UUIDGenerator } from '../../../../utils';

enum InheritedItem {
  Triggers = 'triggers',
  Notifications = 'notifications',
  ParameterConfig = 'parameterConfig',
  ExpectedArtifacts = 'expectedArtifacts',
}

export class PipelineTemplateV2Service {
  public static createPipelineTemplate(pipeline: IPipeline, owner: string): IPipelineTemplateV2 {
    return {
      id: UUIDGenerator.generateUuid(),
      metadata: {
        description: `A pipeline template derived from pipeline "${pipeline.name}" in application "${pipeline.application}"`,
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

  public static isV2PipelineConfig(pipelineConfig: Partial<IPipeline>): boolean {
    return pipelineConfig.schema === PipelineTemplateV2Service.schema;
  }

  public static idForTemplate(template: { id: string; digest?: string }): string {
    const { id, digest = '' } = template;
    return `${id}:${digest}`;
  }

  public static getTemplateVersion({ digest, tag, id }: IPipelineTemplateV2): string {
    if (digest) {
      return `${id}@sha256:${digest}`;
    } else if (tag) {
      return `${id}:${tag}`;
    } else {
      return id;
    }
  }

  public static convertTemplateVersionToId(templateVersion: string): string {
    const versionSplitOnDigest = templateVersion.split('@');
    return versionSplitOnDigest.length > 1 ? versionSplitOnDigest[0] : versionSplitOnDigest[0].split(':')[0];
  }

  public static getPipelineTemplateConfigV2(source: string): IPipelineTemplateConfigV2 {
    // Scoped to Front50 in the short-term.
    return {
      schema: PipelineTemplateV2Service.schema,
      template: {
        artifactAccount: 'front50ArtifactCredentials',
        reference: this.prefixSource(source),
        type: 'front50/pipelineTemplate',
      },
      type: 'templatedPipeline',
    };
  }

  private static prefixSource(source = ''): string {
    const referencePrefix = 'spinnaker://';
    return source.startsWith(referencePrefix) ? source : `${referencePrefix}${source}`;
  }

  public static filterInheritedConfig(pipelineConfig: Partial<IPipeline>) {
    PipelineTemplateV2Service.inheritedKeys.forEach((key) => {
      if (Array.isArray(pipelineConfig[key])) {
        const configCollection = pipelineConfig[key];
        pipelineConfig[key] = (configCollection as any[]).filter((item) => !item.inherited);
      }
    });
    return pipelineConfig;
  }

  private static schema = 'v2';

  public static inheritedKeys: Set<InheritedItem> = new Set([
    InheritedItem.Triggers,
    InheritedItem.Notifications,
    InheritedItem.ParameterConfig,
    InheritedItem.ExpectedArtifacts,
  ]);
}
