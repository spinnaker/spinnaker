import {module, IPromise, IQService} from 'angular';
import {flatten} from 'lodash';
import {Api, API_SERVICE} from 'core/api/api.service';
import {IPipeline} from 'core/domain/IPipeline';
import autoBindMethods from 'class-autobind-decorator';

export interface IPipelineTemplate {
  id: string;
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

export interface IPipelineConfig {
  type: string;
  plan?: boolean;
  config: {
    schema: string;
    pipeline: {
      name: string;
      application: string;
      template: {
        source: string;
      }
      variables: {[key: string]: any};
    }
  }
}

@autoBindMethods
export class PipelineTemplateService {

  constructor(private API: Api, private $q: IQService) { 'ngInject'; }

  public getPipelineTemplateFromSourceUrl(/* source: string */): IPromise<IPipelineTemplate> {
    // Uncomment when the Gate endpoint has been created:
    // return api.one('pipelineTemplates').withParams({source}).get();
    return this.$q.resolve(null); // Remove once Gate endpoint has been created.
  }

  public getPipelinePlan(config: IPipelineConfig): IPromise<IPipeline> {
    return this.API.one('pipelines').one('start').post(config);
  }

  public getPipelineTemplatesByScope(scope: string): IPromise<IPipelineTemplate[]> {
    return this.API.one('pipelineTemplates').withParams({scope}).get();
  }

  public getPipelineTemplatesByScopes(scopes: string[]): IPromise<IPipelineTemplate[]> {
    return this.$q.all(scopes.map(this.getPipelineTemplatesByScope)).then(flatten);
  }
}

export let pipelineTemplateService: PipelineTemplateService;
export const PIPELINE_TEMPLATE_SERVICE = 'spinnaker.core.pipelineTemplate.service';
module(PIPELINE_TEMPLATE_SERVICE, [API_SERVICE])
  .service('pipelineTemplateService', PipelineTemplateService)
  .run(($injector: any) => pipelineTemplateService = <PipelineTemplateService>$injector.get('pipelineTemplateService'));
