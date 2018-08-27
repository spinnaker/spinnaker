import { IComponentController, module } from 'angular';
import { $log } from 'ngimport';
import { IKayentaStage, KayentaAnalysisType } from 'kayenta/domain';

class ForAnalysisTypeController implements IComponentController {
  private stage: IKayentaStage;
  private types: string;

  public showForAnalysisType = (): boolean => {
    if (!this.stage) {
      $log.warn('No stage bound to <for-analysis-type> component');
    }
    if (!this.types) {
      $log.warn('No types bound to <for-analysis-type> component');
    }

    return (this.types || '')
      .split(',')
      .map(type => {
        const val = type.trim() as KayentaAnalysisType;
        if (![KayentaAnalysisType.Retrospective,
              KayentaAnalysisType.RealTime,
              KayentaAnalysisType.RealTimeAutomatic].includes(val)) {
          $log.warn(`${val} is not a Kayenta analysis type.`);
        }
        return val;
      })
      .includes(this.stage.analysisType);
  }
}

export const FOR_ANALYSIS_TYPE_COMPONENT = 'spinnaker.kayenta.forAnalysisType.component';
module(FOR_ANALYSIS_TYPE_COMPONENT, [])
  .component('forAnalysisType', {
    bindings: {
      types: '@',
      stage: '<',
    },
    controller: ForAnalysisTypeController,
    transclude: true,
    template: '<ng-transclude ng-if="$ctrl.showForAnalysisType()"></ng-transclude>',
  });
