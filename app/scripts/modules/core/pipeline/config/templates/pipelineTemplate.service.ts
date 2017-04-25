import {module, IPromise} from 'angular';
import {$q} from 'ngimport'; // Remove once Gate endpoint has been created.
// import {api} from 'core/api/api.service'; // Uncomment once Gate endpoint has been created.

const resolvedTemplate = require('./template.json');

export interface IPipelineTemplate {
  id: string;
  metadata: IPipelineTemplateMetadata;
  protect: boolean;
  schema: string;
  source: string;
  stages: IPipelineTemplateStage[];
  variables: IPipelineTemplateVariable[];
}

export interface IPipelineTemplateMetadata {
  description: string;
  name: string;
  owner: string;
}

export interface IPipelineTemplateVariable {
  defaultValue: any;
  description: string;
  example: string;
  group: string;
  name: string;
  type: 'int' | 'float' | 'list' | 'object' | 'string';
}

export interface IPipelineTemplateStage {
  dependsOn: string[];
  id: string;
  name: string;
  type: string;
}

export class PipelineTemplateService {
  public getPipelineTemplateFromSourceUrl(/* source: string */): IPromise<IPipelineTemplate> {
    // Uncomment when the Gate endpoint has been created:
    // return api.one('pipelineTemplates').withParams({source}).get();
    return $q.resolve(resolvedTemplate); // Remove once Gate endpoint has been created.
  }
}

export let pipelineTemplateService: PipelineTemplateService;
export const PIPELINE_TEMPLATE_SERVICE = 'spinnaker.core.pipelineTemplate.service';
module(PIPELINE_TEMPLATE_SERVICE, [])
  .service('pipelineTemplateService', PipelineTemplateService)
  .run(($injector: any) => pipelineTemplateService = <PipelineTemplateService>$injector.get('pipelineTemplateService'));
