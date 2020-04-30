import { IController, IScope } from 'angular';

export class KubernetesV2UndoRolloutManifestConfigCtrl implements IController {
  public static $inject = ['$scope'];
  constructor(private $scope: IScope) {
    if (this.$scope.stage.isNew) {
      Object.assign(this.$scope.stage, {
        location: '',
        account: '',
        cloudProvider: 'kubernetes',
        numRevisionsBack: 1,
      });
    }
  }

  public handleManifestSelectorChange = (): void => {
    this.$scope.$applyAsync();
  };
}
