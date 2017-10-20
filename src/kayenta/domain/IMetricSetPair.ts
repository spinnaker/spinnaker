export interface IMetricSetPair {
  name: string;
  id: string;
  tags: {[key: string]: string};
  values: {[key: string]: number[]};
}
