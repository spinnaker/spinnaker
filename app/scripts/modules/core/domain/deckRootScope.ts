import {IStateService} from 'angular-ui-router';

export interface IDeckRootScope extends ng.IRootScopeService {
  $state: IStateService;
  authenticating: boolean;
  feature: any;
  pageTitle: string;
  routing: boolean;
}
