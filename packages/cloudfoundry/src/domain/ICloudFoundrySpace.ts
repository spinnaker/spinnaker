export interface ICloudFoundrySpace {
  id?: string;
  name: string;
  organization: ICloudFoundryOrganization;
}

export interface ICloudFoundryOrganization {
  id?: string;
  name: string;
}
