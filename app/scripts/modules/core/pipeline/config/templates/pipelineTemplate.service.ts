import {module, IPromise} from 'angular';
import {$q} from 'ngimport'; // Remove once Gate endpoint has been created.
import {Api, API_SERVICE} from 'core/api/api.service';
import {IPipeline} from 'core/domain/IPipeline';

const resolvedTemplate = require('./template.json');

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

export class PipelineTemplateService {

  static get $inject() { return ['API']; }

  constructor(private API: Api) { }

  public getPipelineTemplateFromSourceUrl(/* source: string */): IPromise<IPipelineTemplate> {
    // Uncomment when the Gate endpoint has been created:
    // return api.one('pipelineTemplates').withParams({source}).get();
    return $q.resolve(resolvedTemplate); // Remove once Gate endpoint has been created.
  }

  public getPipelinePlan(config: IPipelineConfig): IPromise<IPipeline> {
    return this.API.one('pipelines').one('start').post(config);
  }
}

export let pipelineTemplateService: PipelineTemplateService;
export const PIPELINE_TEMPLATE_SERVICE = 'spinnaker.core.pipelineTemplate.service';
module(PIPELINE_TEMPLATE_SERVICE, [API_SERVICE])
  .service('pipelineTemplateService', PipelineTemplateService)
  .run(($injector: any) => pipelineTemplateService = <PipelineTemplateService>$injector.get('pipelineTemplateService'));
