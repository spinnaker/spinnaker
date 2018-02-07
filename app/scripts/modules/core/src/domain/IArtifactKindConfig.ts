export interface IArtifactKindConfig {
  label: string;
  description: string;
  key: string;
  isDefault: boolean;
  isMatch: boolean;
  template: string;
  controller: Function;
  controllerAs?: string;
}
