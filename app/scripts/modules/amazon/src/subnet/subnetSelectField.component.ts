import { IComponentOptions, IController, module } from 'angular';
import { get } from 'lodash';

import { Application, ISubnet } from '@spinnaker/core';
import { AWSProviderSettings } from '../aws.settings';

class SubnetSelectFieldController implements IController {
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
    if (this.hideClassic) {
      return;
    }

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
      this.hideClassic = classicWhitelist.every(
        e => e.region !== this.region || e.credentials !== this.component.credentials,
      );
    }
  }

  private configureSubnets(): void {
    const subnets = this.subnets || [];
    this.activeSubnets = subnets.filter(s => !s.deprecated);
    this.deprecatedSubnets = subnets.filter(s => s.deprecated);
    if (subnets.length) {
      if (this.component[this.field] === null && !this.readOnly) {
        this.component[this.field] = subnets[0].purpose;
        if (this.onChange) {
          this.onChange();
        }
      }
    }
  }
}

class SubnetSelectFieldComponent implements IComponentOptions {
  public bindings: any = {
    subnets: '<',
    component: '<',
    field: '@',
    region: '<',
    onChange: '&',
    labelColumns: '@',
    helpKey: '@',
    readOnly: '<',
    application: '<',
    hideClassic: '<',
  };
  public controller: any = SubnetSelectFieldController;
  public templateUrl: string = require('./subnetSelectField.component.html');
}

export class SubnetSelectFieldWrapperComponent implements IComponentOptions {
  public bindings: any = {
    subnets: '<',
    component: '<',
    field: '<',
    region: '<',
    onChange: '<',
    labelColumns: '<',
    helpKey: '<',
    readOnly: '<',
    application: '<',
    hideClassic: '<',
  };
  public template = `
    <subnet-select-field
      subnets="$ctrl.subnets"
      component="$ctrl.component"
      field="{{::$ctrl.field}}"
      region="$ctrl.region"
      on-change="$ctrl.onChange()"
      label-columns="{{::$ctrl.labelColumns}}"
      help-key="{{::$ctrl.helpKey}}"
      read-only="$ctrl.readOnly"
      application="$ctrl.application"
      hide-classic="$ctrl.hideClassic"
    ></subnet-select-field>
  `;
}

export const SUBNET_SELECT_FIELD_COMPONENT = 'spinnaker.amazon.subnet.subnetSelectField.component';
module(SUBNET_SELECT_FIELD_COMPONENT, [])
  .component('subnetSelectField', new SubnetSelectFieldComponent())
  .component('subnetSelectFieldWrapper', new SubnetSelectFieldWrapperComponent());
