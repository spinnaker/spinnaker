import { IPromise } from 'angular';
import { $q } from 'ngimport';
import { flatten } from 'lodash';
import { API } from 'core/api/ApiService';
import { IPipeline } from 'core/domain/IPipeline';
import { IPipelineTemplateV2 } from 'core/domain/IPipelineTemplateV2';
import { IPipelineTemplateConfigV2 } from 'core/domain';
import { PipelineTemplateV2Service } from 'core/pipeline';

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
  config: {
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
  ): IPromise<IPipelineTemplate> {
    return API.one('pipelineTemplates')
      .one('resolve')
      .withParams({ source, executionId, pipelineConfigId })
      .get()
      .then((template: IPipelineTemplate) => {
        template.selfLink = source;
        return template;
      });
  }

  public static getPipelinePlan(
    config: IPipelineTemplateConfig | IPipelineTemplateConfigV2,
    executionId?: string,
  ): IPromise<IPipeline> {
    const urls = PipelineTemplateV2Service.isV2PipelineConfig(config)
      ? ['v2', 'pipelineTemplates', 'plan']
      : ['pipelines', 'start'];

    return API.one(...urls).post({ ...config, plan: true, executionId });
  }

  public static getPipelineTemplatesByScope = (scope: string): IPromise<IPipelineTemplate[]> => {
    return API.one('pipelineTemplates')
      .withParams({ scope })
      .get();
  };

  public static getPipelineTemplatesByScopes(scopes: string[]): IPromise<IPipelineTemplate[]> {
    return $q
      .all(scopes.map(this.getPipelineTemplatesByScope))
      .then(templates => flatten(templates))
      .then(templates => {
        templates.forEach(template => (template.selfLink = `spinnaker://${template.id}`));
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

  public static getV2PipelineTemplateList(): IPromise<IPipelineTemplateV2[]> {
    return API.one('pipelineTemplates')
      .get()
      .then((templates: IPipelineTemplateV2[]) => {
        return templates.filter(
          ({ digest, schema, tag }) =>
            schema === 'v2' && tag === PipelineTemplateV2Service.defaultTag && typeof digest === 'undefined',
        );
      });
  }
}
