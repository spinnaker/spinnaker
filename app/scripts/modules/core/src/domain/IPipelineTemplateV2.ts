import { IPipeline } from 'core/domain';
import { VariableType } from 'core/pipeline/config/templates/PipelineTemplateReader';

export interface IPipelineTemplateV2 {
  id: string;
  metadata: IPipelineTemplateMetadataV2;
  pipeline: IPipeline;
  protect: boolean;
  schema: string;
  variables: IVariableMetadataV2[];
}

interface IPipelineTemplateMetadataV2 {
  description: string;
  name: string;
  owner: string;
  scopes: string[];
}

interface IVariableMetadataV2 {
  defaultValue?: any;
  description?: string;
  name: string;
  type: VariableType;
}
