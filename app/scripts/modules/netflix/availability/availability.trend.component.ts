import { module } from 'angular';
import { Line, line } from 'd3-shape';
import { scaleLinear, scaleLog } from 'd3-scale';

import { IAvailabilityWindow } from './availability.read.service';

import './availability.less';

interface Dot {
  r: number;
  cx: string;
  cy: string;
  score: number;
}

export class AvailabilityTrendController implements ng.IComponentController {
  public availabilityWindow: IAvailabilityWindow;
  public datetime: string[];
  public height: number;
  public width: number;
  public trendLine: string;
  public dots: Dot[] = [];
  public popoverOpen: boolean[] = [];
  public popoverTemplate: string = require('./availability.trend.popover.html');
  public popoverContents: string[] = [];

  private margin = 5;
  private popoverClose: ng.IPromise<void>[] = [];

  static get $inject() { return ['$timeout']; }

  constructor(private $timeout: ng.ITimeoutService) {}

  // Based on: https://en.wikipedia.org/wiki/High_availability#.22Nines.22
  private getNines(availability: number): number {
    return -Math.log10((100 - availability) / 100);
  }

  // Inverse of getNines
  private getAvailability (nines: number): number {
    return 100 - (Math.pow(10, -nines) * 100);
  }

  private getScore(availability: number): number {
    if (this.getNines(availability) >= this.availabilityWindow.target_nines * 0.95) { return 2; }
    return 4;
  }

  private generateDots(xScale: Function, yScale: Function): Dot[] {
    const dots: Dot[] = [];

    this.availabilityWindow.ts.is_outage.forEach((isOutage, index) => {
      if (isOutage) {
        dots.push({
          r: 3,
          cx: xScale(index),
          cy: yScale(this.availabilityWindow.ts.availability[index]),
          score: this.getScore(this.availabilityWindow.ts.availability[index])
        });
      }
    });

    return dots;
  }

  private updateData(): void {
    if (this.availabilityWindow && this.availabilityWindow.ts.availability && this.availabilityWindow.ts.availability.length) {
      // Set the min value to a large fraction of target nines
      const minValue = this.getAvailability(this.availabilityWindow.target_nines) * 0.995;

      // Create line function
      const xScale = scaleLinear().domain([0, this.availabilityWindow.ts.availability.length]).range([this.margin, this.width]);
      const yScale = scaleLog().domain([minValue, 100]).range([this.height - this.margin, this.margin]).clamp(true);
      const thisLine: Line<number> = line<number>()
                      .x((_, i) => xScale(i))
                      .y((d) => yScale(d));

      // Generate line
      this.trendLine = thisLine(this.availabilityWindow.ts.availability);

      // Generate the dots
      this.dots = this.generateDots(xScale, yScale);
    }
  }

  public $onInit(): void {
    this.updateData();
  }

  public $onChanges(): void {
    this.updateData();
  }

  public showPopover(index: number): void {
    this.popoverOpen[index] = true;
    this.popoverHovered(index);
  }

  public hidePopover(index: number, defer: boolean): void {
    if (defer) {
      this.popoverClose[index] = this.$timeout(
        () => {
          this.popoverOpen[index] = false;
        },
        300);
    } else {
      this.popoverOpen[index] = false;
    }
  }

  public popoverHovered(index: number): void {
    if (this.popoverClose[index]) {
      this.$timeout.cancel(this.popoverClose[index]);
      this.popoverClose[index] = null;
    }
  }
}

class AvailabilityTrendComponent implements ng.IComponentOptions {
  public bindings: any = {
    availabilityWindow: '<',
    height: '<',
    width: '<'
  };

  public controller: any = AvailabilityTrendController;
  public templateUrl: string = require('./availability.trend.html');
}

export const AVAILABILITY_TREND_COMPONENT = 'spinnaker.netflix.availability.trend.component';

module(AVAILABILITY_TREND_COMPONENT, [])
.component('availabilityTrend', new AvailabilityTrendComponent());
