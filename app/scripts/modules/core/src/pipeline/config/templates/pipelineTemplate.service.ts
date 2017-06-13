import { module, IPromise, IQService } from 'angular';
import { flatten } from 'lodash';
import { Api, API_SERVICE } from 'core/api/api.service';
import { IPipeline } from 'core/domain/IPipeline';
import autoBindMethods from 'class-autobind-decorator';

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

export type VariableType = 'int' | 'float' | 'list' | 'object' | 'string';

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
      }
      variables?: { [key: string]: any };
    }
  }
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
  details: {[key: string]: string};
  nestedErrors: IPipelineTemplatePlanError[];
}

@autoBindMethods
export class PipelineTemplateService {

  constructor(private API: Api, private $q: IQService) {
    'ngInject';
  }

  public getPipelineTemplateFromSourceUrl(source: string): IPromise<IPipelineTemplate> {
    return this.API.one('pipelineTemplates').one('resolve').withParams({source}).get()
      .then((template: IPipelineTemplate) => {
        template.selfLink = source;
        return template;
      });
  }

  public getPipelinePlan(config: IPipelineTemplateConfig): IPromise<IPipeline> {
    return this.API.one('pipelines').one('start').post(Object.assign({}, config, {plan: true}));
  }

  public getPipelineTemplatesByScope(scope: string): IPromise<IPipelineTemplate[]> {
    return this.API.one('pipelineTemplates').withParams({scope}).get();
  }

  public getPipelineTemplatesByScopes(scopes: string[]): IPromise<IPipelineTemplate[]> {
    return this.$q.all(scopes.map(this.getPipelineTemplatesByScope)).then(flatten)
      .then(templates => {
        templates.forEach(template => template.selfLink = `spinnaker://${template.id}`);
        return templates;
      });
  }
}

export const PIPELINE_TEMPLATE_SERVICE = 'spinnaker.core.pipelineTemplate.service';
module(PIPELINE_TEMPLATE_SERVICE, [API_SERVICE])
  .service('pipelineTemplateService', PipelineTemplateService);
