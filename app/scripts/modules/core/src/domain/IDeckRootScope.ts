import { IRootScopeService } from 'angular';
import { StateService } from 'angular-ui-router';

export interface IDeckRootScope extends IRootScopeService {
  $state: StateService;
  authenticating: boolean;
  feature: any;
  pageTitle: string;
  routing: boolean;
}
