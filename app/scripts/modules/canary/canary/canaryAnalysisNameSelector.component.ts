import { IComponentController, IComponentOptions, module } from 'angular';

import { Api, API_SERVICE, SETTINGS } from '@spinnaker/core';

class CanaryAnalysisNameSelectorController implements IComponentController {

  public nameList: string[] = [];
  public queryListUrl: string;

  constructor(private API: Api) { 'ngInject'; }

  public $onInit(): void {
    this.queryListUrl = SETTINGS.canaryConfig ? SETTINGS.canaryConfig.queryListUrl : null;
    this.API.one('canaryConfig').all('names').getList()
      .then((results: string[]) => this.nameList = results.sort())
      .catch(() => { this.nameList = [] });
  }
}

class CanaryAnalysisNameSelectorComponent implements IComponentOptions {

  public bindings: any = {
    model: '=',
    className: '@'
  };
  public controller: any = CanaryAnalysisNameSelectorController;
  public templateUrl: string = require('./canaryAnalysisNameSelector.component.html');
}

export const CANARY_ANALYSIS_NAME_SELECTOR_COMPONENT = 'spinnaker.canary.canaryAnalysisNameSelector.component';
module(CANARY_ANALYSIS_NAME_SELECTOR_COMPONENT, [API_SERVICE])
  .component('canaryAnalysisNameSelector', new CanaryAnalysisNameSelectorComponent());
