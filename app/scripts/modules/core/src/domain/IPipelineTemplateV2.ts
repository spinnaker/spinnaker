import { IPipeline } from './IPipeline';
import { VariableType } from '../pipeline/config/templates/PipelineTemplateReader';

export interface IPipelineTemplateV2 {
  id: string;
  metadata: IPipelineTemplateMetadataV2;
  pipeline: IPipeline;
  protect: boolean;
  schema: string;
  variables: IVariableMetadataV2[];
  version?: string;
  updateTs?: string;
  digest?: string;
  tag?: string;
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

export interface IPipelineTemplatePlanV2 extends IPipeline {
  appConfig: { [key: string]: any };
  templateVariables: { [key: string]: any };
}

export interface IPipelineTemplateV2Collections {
  [key: string]: IPipelineTemplateV2[];
}

export interface IPipelineTemplateV2VersionSelections {
  [key: string]: string;
}
