import { IPromise } from 'angular';
import { API } from 'core/api';

export interface IPreconfiguredJobParameter {
  name: string;
  label: string;
  description?: string;
  type: string;
  defaultValue?: string;
}

export interface IPreconfiguredJob {
  type: string;
  uiType: 'CUSTOM' | 'BASIC';
  label: string;
  noUserConfigurableFields: boolean;
  description?: string;
  waitForCompletion?: boolean;
  parameters?: IPreconfiguredJobParameter[];
  producesArtifacts: boolean;
}

export const PreconfiguredJobReader = {
  list(): IPromise<IPreconfiguredJob[]> {
    return API.one('jobs')
      .all('preconfigured')
      .useCache()
      .getList();
  },
};
