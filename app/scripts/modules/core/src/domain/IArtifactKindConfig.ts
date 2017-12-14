export interface IArtifactKindConfig {
  label: string;
  description: string;
  key: string;
  template: string;
  controller: Function;
  controllerAs?: string;
}
