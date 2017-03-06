import { module } from 'angular';

import { DirectiveFactory } from 'core/utils/tsDecorators/directiveFactoryDecorator';
import { IAvailabilityData, IAvailabilityWindow, AVAILABILITY_READER_SERVICE, AvailabilityReaderService } from './availability.read.service';
import { AVAILABILITY_DONUT_COMPONENT } from './availability.donut.component';
import { AVAILABILITY_TREND_COMPONENT } from './availability.trend.component';

import './availability.less';

interface IAggregateDetails {
  score: number;
  reason: string;
}

export class AvailabilityController implements ng.IComponentController {
  private activeRefresher: any;

  static get $inject() {
    return ['$scope', 'availabilityReaderService', 'schedulerFactory'];
  }

  public constructor (private $scope: any, private availabilityReaderService: AvailabilityReaderService, private schedulerFactory: any) {}

  private getWindowScore (window: IAvailabilityWindow): number {
    if (window.nines >= window.target_nines) { return 1; }
    if (window.nines >= window.target_nines * 0.95) { return 2; }
    return 4;
  }

  private getAggregateScore (result: IAvailabilityData): IAggregateDetails {
    let score = 1;
    let reason = 'ALL AVAILABILITY<br/>GOALS MET!';

    // Figure out score
    if (result.override.value === true) {
      score = 4;
      reason = result.override.reason;
    } else if (result.trends.yesterday.score > 1) {
      // If there were recent incidents yesterday
      score = 4;
      reason = 'Yesterday\'s GOAL <strong>NOT</strong> MET';
      // TODO: Add incident links to details
    } else if (result.trends['28days'].score > 1 && result.trends['91days'].score > 1) {
      // If we have not acheived 28 day availability goals
      score = 3;
      reason = '28 DAY GOAL AND <br/>91 DAY GOAL <strong>NOT</strong> MET';
    } else if (result.trends['28days'].score > 1) {
      // If we have not acheived 28 day availability goals (but rest are acheived)
      score = 2;
      reason = '28 DAY GOAL <strong>NOT</strong> MET';
    } else if (result.trends['91days'].score > 1) {
      // If we have not acheived 90 day availability goals (but rest are acheived)
      score = 2;
      reason = '91 DAY GOAL <strong>NOT</strong> MET';
    }

    return { score, reason };
  }

  public refreshData(): void {
    this.availabilityReaderService.getAvailabilityData().then((result) => {
      if (result) {
        // Build composite availability score for main button
        this.$scope.lastUpdated = result.trends.last_updated;
        this.$scope.yesterday = result.trends.yesterday;
        this.$scope.yesterday.score = this.getWindowScore(this.$scope.yesterday);
        this.$scope.twentyeightdays = result.trends['28days'];
        this.$scope.twentyeightdays.score = this.getWindowScore(this.$scope.twentyeightdays);
        this.$scope.ninetyonedays = result.trends['91days'];
        this.$scope.ninetyonedays.score = this.getWindowScore(this.$scope.ninetyonedays);

        const aggregate: IAggregateDetails = this.getAggregateScore(result);
        this.$scope.aggregate = aggregate;
      }
    });
  }

  public initialize(): void {
    this.activeRefresher = this.schedulerFactory.createScheduler();
    this.activeRefresher.subscribe(() => {
      this.refreshData();
    });
    this.refreshData();
  }

  public $onDestroy(): void {
    this.activeRefresher.unsubscribe();
  }
}

@DirectiveFactory('availabilityReaderService')
class AvailabilityDirective implements ng.IDirective {
  public restrict = 'E';
  public controller: any = AvailabilityController;
  public controllerAs = '$ctrl';
  public templateUrl: string = require('./availability.directive.html');
  public replace = true;

  link($scope: ng.IScope, _$element: JQuery) {
    const $ctrl: AvailabilityController = $scope['$ctrl'];
    $ctrl.initialize();
  }
}

export const AVAILABILITY_DIRECTIVE = 'spinnaker.netflix.availability.directive';
module(AVAILABILITY_DIRECTIVE, [
  AVAILABILITY_READER_SERVICE,
  AVAILABILITY_DONUT_COMPONENT,
  AVAILABILITY_TREND_COMPONENT,
  require('core/scheduler/scheduler.factory')
]).directive('availability', <any>AvailabilityDirective);
