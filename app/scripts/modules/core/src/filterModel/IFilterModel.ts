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

export interface ITrueKeyModel {
  [key: string]: boolean;
}

// The sortFilter objects are generated, so leaving all fields as required
// In addition, there should technically be a few different ISortFilter
// sub-interfaces (Clusters, Pipeline, Load Balancer, etc)
// but I want to delete all this stuff in favor of router params eventually
// anyway, so keeping the interface consolidated for now.
export interface ISortFilter {
  account: ITrueKeyModel;
  availabilityZone: ITrueKeyModel;
  awaitingJudgement: boolean;
  category: { [key: string]: any };
  clusters: { [key: string]: any };
  count: number;
  detail: ITrueKeyModel;
  filter: string;
  groupBy: string;
  instanceSort: string;
  instanceType: ITrueKeyModel;
  labels: ITrueKeyModel;
  listInstances: boolean;
  loadBalancerType: ITrueKeyModel;
  maxInstances: number;
  minInstances: number;
  multiselect: boolean;
  pipeline: ITrueKeyModel;
  providerType: ITrueKeyModel;
  region: ITrueKeyModel;
  showAllInstances: boolean;
  showInstances: boolean;
  showLoadBalancers: boolean;
  showServerGroups: boolean;
  showDurations: boolean;
  stack: ITrueKeyModel;
  status: ITrueKeyModel;
  tags: { [key: string]: any };
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
