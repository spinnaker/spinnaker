import { module, IComponentOptions, IComponentController } from 'angular';
import { get, without } from 'lodash';

interface IGceDisk {
  type: string;
  sizeGb: number;
  sourceImage?: string;
}

class GceDiskConfigurerController implements IComponentController {
  public localSSDCount: number;
  public persistentDisks: IGceDisk[];
  public isDefault: boolean;

  // From component bindings.
  public command: any;
  public disks: IGceDisk[];
  private updateDisks: (arg: { disks: IGceDisk[] }) => void;

  public $onInit(): void {
    this.setLocalSSDCount();
    this.setPersistentDisks();

    if (
      this.getLocalSSDDisks().length &&
      !get(this.command, 'viewState.instanceTypeDetails.storage.localSSDSupported')
    ) {
      this.updateDisks({ disks: this.sortDisks(this.getPersistentDisks()) });
    }

    this.isDefault = get(this.command, 'viewState.instanceTypeDetails.storage.isDefault', false);
  }

  public $onChanges(): void {
    this.$onInit();
  }

  public handleLocalSSDCountChange(): void {
    // Handles invalid input.
    if (this.localSSDCount === undefined) {
      return;
    }

    let disks = this.getPersistentDisks();
    for (let i = 0; i < this.localSSDCount; i++) {
      disks = disks.concat([{ type: 'local-ssd', sizeGb: 375 }]);
    }

    disks = this.sortDisks(disks);
    this.updateDisks({ disks });
  }

  public handlePersistentDiskChange(): void {
    let disks = this.persistentDisks.concat(this.getLocalSSDDisks());
    disks = this.sortDisks(disks);
    this.updateDisks({ disks });
  }

  public addPersistentDisk(): void {
    this.persistentDisks = this.persistentDisks.concat([{ type: 'pd-ssd', sizeGb: 10, sourceImage: null }]);
    this.handlePersistentDiskChange();
  }

  public removePersistentDisk(index: number): void {
    this.persistentDisks.splice(index, 1);
    this.handlePersistentDiskChange();
  }

  private setPersistentDisks(): void {
    this.persistentDisks = this.getPersistentDisks();
  }

  private setLocalSSDCount(): void {
    this.localSSDCount = this.getLocalSSDDisks().length;
  }

  private sortDisks(disks: IGceDisk[]): IGceDisk[] {
    const diskWithoutImage = disks.find(disk => disk.type.startsWith('pd-') && disk.sourceImage === undefined);
    return [diskWithoutImage].concat(without(disks, diskWithoutImage));
  }

  private getLocalSSDDisks(): IGceDisk[] {
    return (this.command.disks || []).filter((disk: IGceDisk) => disk.type === 'local-ssd');
  }

  private getPersistentDisks(): IGceDisk[] {
    return (this.command.disks || []).filter((disk: IGceDisk) => disk.type.startsWith('pd-'));
  }
}

const gceDiskConfigurer: IComponentOptions = {
  controller: GceDiskConfigurerController,
  bindings: { command: '<', disks: '<', updateDisks: '&' },
  template: `
    <ng-form name="diskConfigurer">
      <div class="form-group">
        <div class="col-md-5 sm-label-right">
          <b>Number of Local SSD Disks</b>
          <help-field key="gce.instance.storage.localSSD"></help-field>
        </div>
        <div class="col-md-2">
          <input type="number"
                 class="form-control input-sm"
                 ng-model="$ctrl.localSSDCount"
                 ng-change="$ctrl.handleLocalSSDCountChange()"
                 required
                 min="0"
                 max="{{$ctrl.command.viewState.instanceTypeDetails.storage.localSSDSupported ? 8 : 0}}"
                 ng-disabled="!$ctrl.command.viewState.instanceTypeDetails.storage.localSSDSupported"/>
        </div>
      </div>
      <div class="form-group">
        <div class="sm-label-left" style="margin-bottom: 5px;">Persistent Disks
         <span class="glyphicon glyphicon-warning-sign"
               style="color: #EEBB3C;"
               ng-if="$ctrl.isDefault && !diskConfigurer.$dirty"
               uib-tooltip="This instance type does not have an explicitly configured persistent disk option and is using the
                            default disk storage specified in settings.js."></span>
        </div>
        <table class="table table-condensed packed tags">
          <thead>
            <tr>
              <th style="width: 25%;">Type</th>
              <th>Size (GB)</th>
              <th style="width: 50%;">Image</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr ng-repeat="disk in $ctrl.persistentDisks">
              <td>
                <ui-select ng-model="disk.type" class="form-control input-sm" on-select="$ctrl.handlePersistentDiskChange()" required>
                  <ui-select-match placeholder="Select...">{{$select.selected}}</ui-select-match>
                  <ui-select-choices repeat="persistentDiskType in $ctrl.command.backingData.persistentDiskTypes | filter: $select.search">
                    <span ng-bind-html="persistentDiskType | highlight: $select.search"></span>
                  </ui-select-choices>
                </ui-select>
              </td>
              <td>
                <input type="number"
                       class="form-control input-sm"
                       ng-model="disk.sizeGb"
                       ng-change="$ctrl.handlePersistentDiskChange()"
                       required
                       min="10"/>
              </td>
              <td>
                <div ng-if="$index === 0">
                  <p class="small" style="margin: 0;" ng-if="$ctrl.command.viewState.mode === 'create' || $ctrl.command.viewState.mode === 'clone'">
                    This disk will use the image selected at the top of this dialogue.
                  </p>
                  <p class="small" style="margin: 0;" ng-if="$ctrl.command.viewState.mode === 'createPipeline' || $ctrl.command.viewState.mode === 'editPipeline'">
                    This disk will use the image inferred from this pipeline's execution context.
                  </p>
                </div>
                <ui-select ng-if="$index > 0"
                           ng-model="disk.sourceImage"
                           ng-change="$ctrl.handlePersistentDiskChange()"
                           class="form-control input-sm"
                           required>
                  <ui-select-match placeholder="Select an image...">{{$select.selected.imageName || 'Select an image...'}}</ui-select-match>
                  <ui-select-choices repeat="image.imageName as image in $ctrl.command.backingData.allImages | filter: { account: $ctrl.command.credentials } | orderBy: 'imageName'">
                    <span ng-bind-html="image.imageName | highlight: $select.search"></span>
                  </ui-select-choices>
                </ui-select>
              </td>
              <td ng-if="$index > 0">
                <a class="btn btn-link sm-label" style="margin-top: 0;" ng-click="$ctrl.removePersistentDisk($index)">
                  <span class="glyphicon glyphicon-trash"></span>
                </a>
              </td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <td colspan="4">
                <button class="btn btn-block btn-sm add-new" ng-click="$ctrl.addPersistentDisk()">
                  <span class="glyphicon glyphicon-plus-sign"></span> Add New Persistent Disk
                </button>
              </td>
            </tr>
          </tfoot>
        </table>
      </div>
    </ng-form>`
};

export const GCE_DISK_CONFIGURER = 'spinnaker.gce.diskConfigurer.component';
module(GCE_DISK_CONFIGURER, []).component('gceDiskConfigurer', gceDiskConfigurer);
