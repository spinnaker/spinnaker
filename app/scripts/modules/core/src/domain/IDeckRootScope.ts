import { IRootScopeService } from 'angular';
import { StateService } from '@uirouter/angularjs';

export interface IDeckRootScope extends IRootScopeService {
  $state: StateService;
  authenticating: boolean;
  feature: any;
  pageTitle: string;
  routing: boolean;
}
