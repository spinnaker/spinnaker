import {StateService} from 'angular-ui-router';

export interface IDeckRootScope extends ng.IRootScopeService {
  $state: StateService;
  authenticating: boolean;
  feature: any;
  pageTitle: string;
  routing: boolean;
}
