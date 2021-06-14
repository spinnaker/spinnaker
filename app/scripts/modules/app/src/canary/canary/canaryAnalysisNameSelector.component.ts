import { IComponentOptions, IController, module } from 'angular';

import { REST, SETTINGS } from '@spinnaker/core';

class CanaryAnalysisNameSelectorController implements IController {
  public nameList: string[] = [];
  public queryListUrl: string;

  public $onInit(): void {
    this.queryListUrl = SETTINGS.canaryConfig ? SETTINGS.canaryConfig.queryListUrl : null;
    REST('/canaryConfig/names')
      .get()
      .then((results: string[]) => (this.nameList = results.sort()))
      .catch(() => {
        this.nameList = [];
      });
  }
}

const canaryAnalysisNameSelectorComponent: IComponentOptions = {
  bindings: {
    model: '=',
    className: '@',
  },
  controller: CanaryAnalysisNameSelectorController,
  templateUrl: require('./canaryAnalysisNameSelector.component.html'),
};

export const CANARY_ANALYSIS_NAME_SELECTOR_COMPONENT = 'spinnaker.canary.canaryAnalysisNameSelector.component';
module(CANARY_ANALYSIS_NAME_SELECTOR_COMPONENT, []).component(
  'canaryAnalysisNameSelector',
  canaryAnalysisNameSelectorComponent,
);
