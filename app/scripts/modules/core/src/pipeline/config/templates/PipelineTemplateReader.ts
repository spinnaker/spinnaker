import { flatten } from 'lodash';
import { $q } from 'ngimport';

import { REST } from '../../../api/ApiService';
import { IPipelineTemplateConfigV2 } from '../../../domain';
import { IPipeline } from '../../../domain/IPipeline';
import { IPipelineTemplateV2Collections } from '../../../domain/IPipelineTemplateV2';

import { PipelineTemplateV2Service } from './v2/pipelineTemplateV2.service';

export interface IPipelineTemplate {
  id: string;
  selfLink?: string;
  metadata: ITemplateMetadata;
  protect: boolean;
  schema: string;
  source: string;
  stages: ITemplateStage[];
  variables: IVariableMetadata[];
}

export interface ITemplateMetadata {
  description: string;
  name: string;
  owner: string;
}

export interface IVariableMetadata {
  defaultValue?: any;
  description?: string;
  example?: string;
  group?: string;
  name: string;
  type: VariableType;
}

export type VariableType = 'int' | 'float' | 'list' | 'object' | 'string' | 'boolean';

export interface ITemplateStage {
  dependsOn: string[];
  id: string;
  name: string;
  type: string;
}

export interface IPipelineTemplateConfig extends Partial<IPipeline> {
  type: string;
  plan?: boolean;
  config?: {
    schema: string;
    pipeline: {
      name: string;
      application: string;
      pipelineConfigId?: string;
      template: {
        source: string;
      };
      variables?: { [key: string]: any };
    };
    configuration?: {
      inherit?: string[];
    };
  };
}

export interface IPipelineTemplatePlanResponse {
  errors: IPipelineTemplatePlanError[];
  message: string;
  status: string;
}

export interface IPipelineTemplatePlanError {
  severity: string;
  message: string;
  location: string;
  cause: string;
  suggestion: string;
  details: { [key: string]: string };
  nestedErrors: IPipelineTemplatePlanError[];
}

export class PipelineTemplateReader {
  public static getPipelineTemplateFromSourceUrl(
    source: string,
    executionId?: string,
    pipelineConfigId?: string,
  ): PromiseLike<IPipelineTemplate> {
    return REST('/pipelineTemplates/resolve')
      .query({ source, executionId, pipelineConfigId })
      .get()
      .then((template: IPipelineTemplate) => {
        template.selfLink = source;
        return template;
      });
  }

  public static getPipelinePlan(
    config: IPipelineTemplateConfig | IPipelineTemplateConfigV2,
    executionId?: string,
  ): PromiseLike<IPipeline> {
    const endpoint = PipelineTemplateV2Service.isV2PipelineConfig(config)
      ? '/v2/pipelineTemplates/plan'
      : '/pipelines/start';
    return REST(endpoint).post({ ...config, plan: true, executionId });
  }

  public static getPipelineTemplatesByScope = (scope: string): PromiseLike<IPipelineTemplate[]> => {
    return REST('/pipelineTemplates').query({ scope }).get();
  };

  public static getPipelineTemplatesByScopes(scopes: string[]): PromiseLike<IPipelineTemplate[]> {
    return $q
      .all(scopes.map(this.getPipelineTemplatesByScope))
      .then((templates) => flatten(templates))
      .then((templates) => {
        templates.forEach((template) => (template.selfLink = `spinnaker://${template.id}`));
        return templates;
      });
  }

  public static getPipelineTemplateConfig({
    name,
    application,
    source,
  }: {
    name: string;
    application: string;
    source: string;
  }): Partial<IPipelineTemplateConfig> {
    return {
      config: {
        schema: '1',
        pipeline: {
          name,
          application,
          template: { source },
        },
      },
    };
  }

  public static getV2PipelineTemplateList(): PromiseLike<IPipelineTemplateV2Collections> {
    return REST('/v2/pipelineTemplates/versions').get();
  }
}
