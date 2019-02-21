import { module, IComponentOptions, IComponentController } from 'angular';
import { get } from 'lodash';

interface IGceAcceleratorType {
  name: string;
  description: string;
  availableCardCounts: number[];
}

interface IGceAcceleratorConfig {
  acceleratorType: string;
  acceleratorCount: number;
}

class GceAcceleratorConfigurerController implements IComponentController {
  public command: any;

  private getAvailableAcceleratorTypes(): IGceAcceleratorType[] {
    return get(this.command, ['viewState', 'acceleratorTypes'], []);
  }

  public addAccelerator(): void {
    this.command.acceleratorConfigs = this.command.acceleratorConfigs || [];
    const types = this.getAvailableAcceleratorTypes();
    if (types.length === 0) {
      return;
    }
    this.command.acceleratorConfigs.push({
      acceleratorType: types[0].name,
      acceleratorCount: 1,
    });
  }

  public removeAccelerator(acceleratorConfig: IGceAcceleratorConfig): void {
    const index = (this.command.acceleratorConfigs || []).indexOf(acceleratorConfig);
    if (index > -1) {
      this.command.acceleratorConfigs.splice(index, 1);
    }
  }

  public getAvailableCardCounts(acceleratorName: string): number[] {
    if (!acceleratorName) {
      return null;
    }
    const config = this.getAvailableAcceleratorTypes().find(a => a.name === acceleratorName);
    if (!config) {
      return null;
    }
    return config.availableCardCounts;
  }

  public onAcceleratorTypeChanged(acceleratorConfig: IGceAcceleratorConfig): void {
    if (!acceleratorConfig) {
      return;
    }
    const counts = this.getAvailableCardCounts(acceleratorConfig.acceleratorType);
    if (!counts) {
      return;
    }
    if (!counts.includes(acceleratorConfig.acceleratorCount)) {
      let nearestCount = 0;
      for (const count of counts) {
        nearestCount = count;
        if (count > acceleratorConfig.acceleratorCount) {
          break;
        }
      }
      acceleratorConfig.acceleratorCount = nearestCount;
    }
  }
}

const gceAcceleratorConfigurer: IComponentOptions = {
  controller: GceAcceleratorConfigurerController,
  bindings: { command: '<' },
  template: `
    <div class="form-group">
      <div class="sm-label-left" style="margin-bottom: 5px;">
        Accelerators
      </div>

      <table class="table table-condensed packed tags">
        <thead>
          <tr>
            <th style="width: 75%;">Type</th>
            <th style="width: 15%;">Count</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr ng-repeat="acceleratorConfig in $ctrl.command.acceleratorConfigs">
            <td>
              <ui-select ng-model="acceleratorConfig.acceleratorType" on-select="$ctrl.onAcceleratorTypeChanged(acceleratorConfig)" class="form-control input-sm">
                <ui-select-match placeholder="Accelerator Type">{{ $select.selected.description }}</ui-select-match>
                <ui-select-choices repeat="accelerator.name as accelerator in $ctrl.command.viewState.acceleratorTypes | filter: $select.search">
                  <span ng-bind-html="accelerator.description | highlight: $select.search"></span>
                </ui-select-choices>
              </ui-select>
            </td>
            <td>
              <ui-select ng-model="acceleratorConfig.acceleratorCount" class="form-control input-sm">
                <ui-select-match placeholder="Accelerator Count">{{ $select.selected }}</ui-select-match>
                <ui-select-choices repeat="count in $ctrl.getAvailableCardCounts(acceleratorConfig.acceleratorType)">
                  {{ count }}
                </ui-select-choices>
            </td>
            <td>
              <a class="btn btn-link sm-label" style="margin-top: 0;" ng-click="$ctrl.removeAccelerator(acceleratorConfig)">
                <span class="glyphicon glyphicon-trash"></span>
              </a>
            </td>
          </tr>
        </tbody>
        <tfoot>
          <tr ng-if="$ctrl.command.acceleratorConfigs && $ctrl.command.acceleratorConfigs.length > 0">
            <td colspan="3">
              Adding Accelerators places constraints on the instances that you can deploy. For a complete list of
              these restrictions see <a href="https://cloud.google.com/compute/docs/gpus/#restrictions">the docs on GPUs</a>.
            </td>
          </tr>
          <tr>
            <td colspan="3">
              <button class="btn btn-block btn-sm add-new" ng-click="$ctrl.addAccelerator()">
                <span class="glyphicon glyphicon-plus-sign"></span> Add Accelerator
              </button>
            </td>
          </tr>
      </div>
        </tfoot>
      </table>
    </div>
  `,
};

export const GCE_ACCELERATOR_CONFIGURER = 'spinnaker.gce.accelerator.component';
module(GCE_ACCELERATOR_CONFIGURER, []).component('gceAcceleratorConfigurer', gceAcceleratorConfigurer);
