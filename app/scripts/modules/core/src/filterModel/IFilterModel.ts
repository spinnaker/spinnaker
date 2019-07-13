export interface IFilterConfig {
  model: keyof ISortFilter;
  param?: string;
  clearValue?: any;
  type?: string;
  filterLabel?: string;
  filterTranslator?: { [key: string]: string };
  displayOption?: boolean;
  defaultValue?: string;
  array?: boolean;
}

// The sortFilter objects are generated, so leaving all fields as required
// In addition, there should technically be a few different ISortFilter
// sub-interfaces (Clusters, Pipeline, Load Balancer, etc)
// but I want to delete all this stuff in favor of router params eventually
// anyway, so keeping the interface consolidated for now.
export interface ISortFilter {
  account: { [key: string]: boolean };
  availabilityZone: { [key: string]: boolean };
  category: { [key: string]: any };
  clusters: { [key: string]: any };
  count: number;
  detail: { [key: string]: boolean };
  filter: string;
  groupBy: string;
  instanceSort: string;
  instanceType: { [key: string]: boolean };
  labels: { [key: string]: boolean };
  listInstances: boolean;
  maxInstances: number;
  minInstances: number;
  multiselect: boolean;
  pipeline: { [key: string]: boolean };
  providerType: { [key: string]: boolean };
  region: { [key: string]: boolean };
  showAllInstances: boolean;
  showInstances: boolean;
  showLoadBalancers: boolean;
  showServerGroups: boolean;
  showDurations: boolean;
  stack: { [key: string]: boolean };
  status: { [key: string]: boolean };
}

export interface IFilterModel {
  config: IFilterConfig[];
  groups: any[];
  tags: any[];
  displayOptions: any;
  sortFilter: ISortFilter;
  addTags: () => void;
  clearFilters: () => void;
  activate: () => void;
  applyParamsToUrl: () => void;
}
