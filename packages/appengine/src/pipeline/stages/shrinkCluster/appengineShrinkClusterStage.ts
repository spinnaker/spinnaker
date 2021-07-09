import { module } from 'angular';

import { Registry } from '@spinnaker/core';

import { AppengineStageCtrl } from '../appengineStage.controller';
import { IAppengineStage, IAppengineStageScope } from '../../../domain/index';

interface IAppengineShrinkClusterStage extends IAppengineStage {
  shrinkToSize: number;
  allowDeleteActive: boolean;
  retainLargerOverNewer: string | boolean;
}

class AppengineShrinkClusterStageCtrl extends AppengineStageCtrl {
  public static $inject = ['$scope'];
  constructor(public $scope: IAppengineStageScope) {
    super($scope);

    super.setAccounts().then(() => {
      super.setStageRegion();
    });
    super.setStageCloudProvider();
    super.setStageCredentials();

    const stage = $scope.stage as IAppengineShrinkClusterStage;
    if (stage.shrinkToSize === undefined) {
      stage.shrinkToSize = 1;
    }

    if (stage.allowDeleteActive === undefined) {
      stage.allowDeleteActive = false;
    }

    if (stage.retainLargerOverNewer === undefined) {
      stage.retainLargerOverNewer = 'false';
    }

    stage.retainLargerOverNewer = stage.retainLargerOverNewer.toString();
  }

  public pluralize(str: string, val: number): string {
    return val === 1 ? str : str + 's';
  }
}

export const APPENGINE_SHRINK_CLUSTER_STAGE = 'spinnaker.appengine.pipeline.stage.shrinkClusterStage';

module(APPENGINE_SHRINK_CLUSTER_STAGE, [])
  .config(function () {
    Registry.pipeline.registerStage({
      provides: 'shrinkCluster',
      key: 'shrinkCluster',
      cloudProvider: 'appengine',
      templateUrl: require('./shrinkClusterStage.html'),
      validators: [
        { type: 'requiredField', fieldName: 'cluster' },
        { type: 'requiredField', fieldName: 'shrinkToSize', fieldLabel: 'shrink to [X] Server Groups' },
        { type: 'requiredField', fieldName: 'credentials', fieldLabel: 'account' },
      ],
    });
  })
  .controller('appengineShrinkClusterStageCtrl', AppengineShrinkClusterStageCtrl);
