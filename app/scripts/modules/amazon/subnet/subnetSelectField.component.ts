import {module} from 'angular';
import {get} from 'lodash';

import {ISubnet} from 'core/domain';
import {Application} from 'core/application/application.model';
import {AWSProviderSettings} from '../aws.settings';

class SubnetSelectFieldController implements ng.IComponentController {
  public subnets: ISubnet[];
  public activeSubnets: ISubnet[];
  public deprecatedSubnets: ISubnet[];
  public hideClassic: boolean;
  public application: Application;
  public component: any;
  public region: string;
  public readOnly: boolean;
  public field: string;
  public onChange: () => any;
  public helpKey: string;
  public labelColumns: string;

  public $onInit(): void {
    this.$onChanges();
  }

  public $onChanges(): void {
    this.setClassicLock();
    this.configureSubnets();
  }

  private setClassicLock(): void {
    const lockoutDate: number = AWSProviderSettings.classicLaunchLockout;
    if (lockoutDate) {
      const appCreationDate: number = Number(get(this.application, 'attributes.createTs', 0));
      if (appCreationDate > lockoutDate) {
        this.hideClassic = true;
        return;
      }
    }
    const classicWhitelist = AWSProviderSettings.classicLaunchWhitelist;
    if (classicWhitelist) {
      this.hideClassic = classicWhitelist.every(e => e.region !== this.region || e.credentials !== this.component.credentials);
    }
  }

  private configureSubnets(): void {
    const subnets = this.subnets || [];
    this.activeSubnets = subnets.filter(s => !s.deprecated);
    this.deprecatedSubnets = subnets.filter(s => s.deprecated);
    if (subnets.length) {
      if (!this.component[this.field] && !this.readOnly) {
        this.component[this.field] = subnets[0].purpose;
        if (this.onChange) {
          this.onChange();
        }
      }
    }
  }
}

class SubnetSelectFieldComponent implements ng.IComponentOptions {
  public bindings: any = {
    subnets: '<',
    component: '<',
    field: '@',
    region: '<',
    onChange: '&',
    labelColumns: '@',
    helpKey: '@',
    readOnly: '<',
    application: '<'
  };
  public controller: any = SubnetSelectFieldController;
  public templateUrl: string = require('./subnetSelectField.component.html');
}

export const SUBNET_SELECT_FIELD_COMPONENT = 'spinnaker.amazon.subnet.subnetSelectField.component';
module(SUBNET_SELECT_FIELD_COMPONENT, [
]).component('subnetSelectField', new SubnetSelectFieldComponent());
