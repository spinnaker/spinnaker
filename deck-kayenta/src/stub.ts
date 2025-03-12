import { UIRouter } from '@uirouter/core';
import { module } from 'angular';
import { registerKayentaDataSourceStubs } from 'kayenta/canary.dataSource.stub';
import 'kayenta/canary.help';
import { CanarySettings } from 'kayenta/canary.settings';
import { registerKayentaStateStubs } from 'kayenta/navigation/canary.states.stub';

import { ApplicationStateProvider, Registry } from '@spinnaker/core';

import { CANARY_SCORE_COMPONENT } from './kayenta/components/canaryScore.component';
import { CANARY_SCORES_CONFIG_COMPONENT } from './kayenta/components/canaryScores.component';
import { KAYENTA_ANALYSIS_TYPE_COMPONENT } from './kayenta/stages/kayentaStage/analysisType.component';
import { FOR_ANALYSIS_TYPE_COMPONENT } from './kayenta/stages/kayentaStage/forAnalysisType.component';
import { kayentaCanaryStage } from './kayenta/stages/kayentaStage/kayentaStage';
import { KayentaStageController } from './kayenta/stages/kayentaStage/kayentaStage.controller';
import { KayentaStageTransformer } from './kayenta/stages/kayentaStage/kayentaStage.transformer';
import { KAYENTA_STAGE_CONFIG_SECTION } from './kayenta/stages/kayentaStage/kayentaStageConfigSection.component';
import { KAYENTA_STAGE_EXECUTION_DETAILS_CONTROLLER } from './kayenta/stages/kayentaStage/kayentaStageExecutionDetails.controller';

/**
 * This is the stub for Kayenta in deck
 *
 * - Registers all AngularJS components and controllers
 * - Registers the full Kayenta Stage with Deck
 * - Registers STUB routes for canary configs and canary reports (see: registerKayentaStateStubs)
 *    - These stub routes are names and URLs only
 *    - A lazyLoad router hook runs when the user navigates to a stub route and loads the remainder of Kayenta
 * - Registers a DataSource that fetches kayenta canary/report data (and provides the nav items)
 *
 * See: lazy.ts for what happens next
 */

export const KAYENTA_MODULE = 'spinnaker.kayenta';

if (CanarySettings.featureDisabled) {
  module(KAYENTA_MODULE, []);
} else {
  module(KAYENTA_MODULE, [
    'ui.router',
    CANARY_SCORES_CONFIG_COMPONENT,
    CANARY_SCORE_COMPONENT,
    FOR_ANALYSIS_TYPE_COMPONENT,
    KAYENTA_ANALYSIS_TYPE_COMPONENT,
    KAYENTA_STAGE_CONFIG_SECTION,
    KAYENTA_STAGE_EXECUTION_DETAILS_CONTROLLER,
  ])
    .controller('KayentaCanaryStageCtrl', KayentaStageController)
    .run([
      '$uiRouter',
      'applicationState',
      function ($uiRouter: UIRouter, applicationState: ApplicationStateProvider) {
        registerKayentaDataSourceStubs();
        registerKayentaStateStubs(applicationState, $uiRouter);
        if (CanarySettings.stagesEnabled !== false) {
          Registry.pipeline.registerStage(kayentaCanaryStage);
          Registry.pipeline.registerTransformer(new KayentaStageTransformer());
        }
      },
    ]);
}
