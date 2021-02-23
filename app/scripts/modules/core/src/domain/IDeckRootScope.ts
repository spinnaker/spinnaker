import { StateService } from '@uirouter/angularjs';
import { IRootScopeService } from 'angular';

export interface IDeckRootScope extends IRootScopeService {
  $state: StateService;
  authenticating: boolean;
  feature: any;
  pageTitle: string;
  routing: boolean;
}
