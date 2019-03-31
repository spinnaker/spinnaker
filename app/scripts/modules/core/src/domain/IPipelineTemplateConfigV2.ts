import { IPipeline } from 'core/domain';

export interface IPipelineTemplateConfigV2 extends IPipeline {
  inherit?: string[];
  schema: string;
  template: {
    artifactAccount: string;
    reference: string;
    type: string;
  };
  type: string;
  variables?: { [key: string]: any };
}
