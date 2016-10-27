import {module} from 'angular';

export class CiFilterModel {
  public searchFilter: string;
}

export const CI_FILTER_MODEL = 'spinnaker.netflix.ci.filter.model';

module(CI_FILTER_MODEL, [])
  .service('CiFilterModel', CiFilterModel);
