import { Ng1StateDeclaration, StateParams } from 'angular-ui-router';

export interface IFilterConfig {
  model: string;
  param?: string;
  clearValue?: string;
  type?: string;
  filterLabel?: string;
  filterTranslator?: {[key: string]: string}
  displayOption?: boolean;
  defaultValue?: string;
}

export interface IFilterModel {
  groups: any[];
  tags: any[];
  displayOptions: any;
  savedState: any;
  sortFilter: any;
  addTags: () => void;
  saveState: (state: Ng1StateDeclaration, params: StateParams, filters: any) => void;
  restoreState: (toParams: StateParams) => void;
  hasSavedState: (toParams: StateParams) => boolean;
  clearFilters: () => void;
  activate: () => void;
  applyParamsToUrl: () => void;
}
