import { IPipeline } from './IPipeline';

export interface IPipelineTemplateConfigV2 extends Partial<IPipeline> {
  exclude?: string[];
  schema: string;
  template: {
    artifactAccount: string;
    reference: string;
    type: string;
  };
  type: string;
  variables?: { [key: string]: any };
}
