import { ITaggedEntity } from './ITaggedEntity';

export interface IFunctionSourceData {
  cloudProvider?: string;
  name?: string;
  provider?: string;
  type?: string;
}

export interface IFunction extends ITaggedEntity {
  account?: string;
  cloudProvider: string;
  description?: string;
  functionName?: string;
  region: string;
  searchField?: string;
  type?: string;
  vpcId?: string;
  vpcName?: string;
}

export interface IFunctionGroup {
  heading: string;
  functionDef?: IFunction;
  subgroups?: IFunctionGroup[];
  searchField?: string;
}
