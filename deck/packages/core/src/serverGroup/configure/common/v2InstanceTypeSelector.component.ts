import type { IComponentController, IComponentOptions, IOnChangesObject, IRootElementService } from 'angular';
import { module } from 'angular';
import React from 'react';
import ReactDOM from 'react-dom';

import { InstanceTypeSelector } from './InstanceTypeSelector';
import type { IServerGroupCommand } from './serverGroupCommandBuilder.service';

import './instanceTypeSelector.directive.less';

export class V2InstanceTypeSelectorController implements IComponentController {
  public static $inject = ['$element'];

  public command: IServerGroupCommand;
  public onTypeChanged: (type: string) => void;

  private linked = false;
  private instanceProfile: string;
  private instanceTypes: string[];
  private virtualizationType: string;

  public constructor(private $element: IRootElementService) {}

  public $postLink(): void {
    this.linked = true;
    this.captureWatchedValues();
    this.renderReactComponent();
  }

  public $onChanges(_changes: IOnChangesObject): void {
    if (this.linked) {
      this.captureWatchedValues();
      this.renderReactComponent();
    }
  }

  public $doCheck(): void {
    if (!this.linked) {
      return;
    }

    const nextInstanceProfile = this.command?.viewState?.instanceProfile;
    const nextInstanceTypes = this.command?.backingData?.filtered?.instanceTypes;
    const nextVirtualizationType = this.command?.virtualizationType;
    if (
      nextInstanceProfile !== this.instanceProfile ||
      nextInstanceTypes !== this.instanceTypes ||
      nextVirtualizationType !== this.virtualizationType
    ) {
      this.captureWatchedValues();
      this.renderReactComponent();
    }
  }

  public $onDestroy(): void {
    ReactDOM.unmountComponentAtNode(this.$element[0]);
  }

  private captureWatchedValues(): void {
    this.instanceProfile = this.command?.viewState?.instanceProfile;
    this.instanceTypes = this.command?.backingData?.filtered?.instanceTypes;
    this.virtualizationType = this.command?.virtualizationType;
  }

  private renderReactComponent(): void {
    ReactDOM.render(
      React.createElement(InstanceTypeSelector, {
        command: this.command,
        onTypeChanged: this.onTypeChanged,
      }),
      this.$element[0],
    );
  }
}

export const v2InstanceTypeSelector: IComponentOptions = {
  bindings: {
    command: '<',
    onTypeChanged: '<',
  },
  controller: V2InstanceTypeSelectorController,
};

export const V2_INSTANCE_TYPE_SELECTOR = 'spinnaker.core.serverGroup.configure.common.v2instanceTypeSelector';
module(V2_INSTANCE_TYPE_SELECTOR, []).component('v2InstanceTypeSelector', v2InstanceTypeSelector);
