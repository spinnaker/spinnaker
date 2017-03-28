export interface ParameterDefinitionList {
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
  parameterDefinitionList: ParameterDefinitionList[];
  url: string;
}
