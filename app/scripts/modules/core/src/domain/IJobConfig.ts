export interface IParameterDefinitionList {
  defaultValue: string;
  description?: string;
  name: string;
}

export interface IJobConfig {
  buildable: boolean;
  concurrentBuild: boolean;
  description: string;
  displayName: string;
  name: string;
  parameterDefinitionList: IParameterDefinitionList[];
  url: string;
}
