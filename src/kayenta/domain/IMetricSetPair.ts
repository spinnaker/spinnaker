export interface IMetricSetPair {
  name: string;
  id: string;
  tags: { [key: string]: string };
  attributes: {
    control: { [key: string]: string };
    experiment: { [key: string]: string };
  };
  values: { [key: string]: number[] };
  scopes: { [key: string]: IMetricSetScope };
}

export interface IMetricSetScope {
  startTimeIso: string;
  startTimeMillis: number;
  stepMillis: number;
}
