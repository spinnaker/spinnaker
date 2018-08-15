export interface IArtifactKindConfig {
  label: string;
  type?: string;
  description: string;
  key: string;
  isDefault: boolean;
  isMatch: boolean;
  isPubliclyAccessible?: boolean;
  template: string;
  controller: Function;
  controllerAs?: string;
}
