import * as $ from 'jquery';
import { get, last, sortBy } from 'lodash';
import { IComponentController, IComponentOptions, IRootScopeService, IScope, ITimeoutService, module } from 'angular';
import { PathNode, StateService } from 'angular-ui-router';

import { Instance } from 'core/domain';

interface IActiveInstance {
  instanceId: string;
  provider: string;
}

export class InstancesController implements IComponentController {
  public instances: Instance[];
  public highlight: string;

  private activeInstance: IActiveInstance;
  private tooltipTargets: Element[] = [];

  public constructor(private $rootScope: IRootScopeService,
                     private $scope: IScope,
                     private $element: JQuery,
                     private $timeout: ITimeoutService,
                     private $state: StateService) {
    'ngInject';
  }

  private removeTooltips(): void {
    this.tooltipTargets.forEach(target => $(target).tooltip('destroy'));
    this.tooltipTargets.length = 0;
  };

  private renderInstances(): void {
    this.activeInstance = null;
    const instances = sortBy(this.instances, 'launchTime');
    const innerHtml = '<div class="instances">' + instances.map((instance) => {
        const id = instance.id,
              params = { instanceId: instance.id, provider: instance.provider };
        let activeClass = '';

        if (this.$state.includes('**.instanceDetails', params)) {
          activeClass = ' active';
          this.activeInstance = params;
        }
        if (this.highlight === id) {
          activeClass += ' highlighted';
        }

        return '<a title="' + id +
          '" data-provider="' + instance.provider +
          '" data-toggle="tooltip" data-instance-id="' + id +
          '" class="instance health-status-' + instance.healthState + activeClass + '"></a>';
      }).join('') + '</div>';

    if (innerHtml !== this.$element.get(0).innerHTML) {
      this.removeTooltips();
      this.$element.get(0).innerHTML = innerHtml;
    }
  }

  private clearActiveState(): void {
    if (this.activeInstance && !this.$state.includes('**.instanceDetails', this.activeInstance)) {
      $('a[data-instance-id="' + this.activeInstance.instanceId + '"]', this.$element).removeClass('active');
      this.activeInstance = null;
    }
  }

  public $onInit(): void {
    const base = last(get<PathNode[]>(this.$element.parent().inheritedData('$uiView'), '$cfg.path')).state.name;

    this.$element.click((event) => {
      this.$timeout(() => {
        if (event.target && event.target.getAttribute('data-instance-id')) {
          // anything handled by ui-sref or actual links should be ignored
          if (event.isDefaultPrevented() || (event.originalEvent && event.originalEvent.defaultPrevented)) {
            return;
          }
          if (this.activeInstance) {
            $('a[data-instance-id="' + this.activeInstance.instanceId + '"]', this.$element).removeClass('active');
          }
          const params: IActiveInstance = {
            instanceId: event.target.getAttribute('data-instance-id'),
            provider: event.target.getAttribute('data-provider')
          };
          this.activeInstance = params;
          // also stolen from uiSref directive
          if (!this.$state.includes('**.instanceDetails', params)) {
            this.$state.go('.instanceDetails', params, {relative: base, inherit: true});
          }
          event.target.className += ' active';
          event.preventDefault();
        }
      });
    });

    this.$element.mouseover((event) => {
      if (!this.tooltipTargets.includes(event.target) && event.target.hasAttribute('data-toggle')) {
        $(event.target).tooltip({animation: false}).tooltip('show');
        this.tooltipTargets.push(event.target);
      }
    });

    this.$rootScope.$on('$locationChangeSuccess', () => this.clearActiveState());

    this.$scope.$watch('instances', () => this.renderInstances());
    this.$scope.$watch('highlight', () => this.renderInstances());
  }

  public $onDestroy(): void {
    this.removeTooltips();
    this.$element.unbind('mouseover');
    this.$element.unbind('click');
  }
}

export class InstancesComponent implements IComponentOptions {
  public bindings: any = {
    instances: '<',
    highlight: '<'
  };
  public controller: any = InstancesController;
}

export const INSTANCES_COMPONENT = 'spinnaker.core.instance.instances.component';
module(INSTANCES_COMPONENT, [])
  .component('instances', new InstancesComponent());
