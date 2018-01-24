import { Application, ISubnet } from '@spinnaker/core';

export interface ISubnetSelectFieldProps {
  application: Application;
  component: {[key: string]: any};
  field: string;
  helpKey: string;
  hideClassic?: boolean;
  labelColumns: number;
  onChange: () => void;
  readOnly?: boolean;
  region: string;
  subnets: ISubnet[];
}
