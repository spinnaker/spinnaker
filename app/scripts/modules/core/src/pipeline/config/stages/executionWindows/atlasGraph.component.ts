import { IController, module } from 'angular';
import { has } from 'lodash';
import { Subject } from 'rxjs';

import { SETTINGS } from 'core/config/settings';
import { DateTime, Duration } from 'luxon';

interface IExecutionWindow {
  displayStart: Date;
  displayEnd: Date;
}

interface IGraphSeries {
  axis: string;
  dataset: string;
  key: string;
  color: string;
  type: string[];
  id: string;
}

interface IAxes {
  x: IAxis;
  y: IAxis;
  x2: IAxis;
  y2: IAxis;
}

interface IAxis {
  ticks: number;
  key?: string;
  type?: string;
  padding?: any;
}

interface IZoom {
  x: boolean;
  y: boolean;
}

interface IChartOptions {
  tooltipHook: (rows: any[]) => any;
  series: IGraphSeries[];
  axes: IAxes;
  zoom: IZoom;
}

interface IChartPoint {
  val: number;
  timestamp: Date;
}

interface IChartData {
  loading: boolean;
  windows: IChartPoint[];
  SPS: IChartPoint[];
}

interface IAtlasRegion {
  label: string;
  baseUrl: string;
}

interface IWindowData {
  start: number;
  end: number;
}

class ExecutionWindowAtlasGraphController implements IController {
  // configured in settings
  public regions: IAtlasRegion[];

  // actual configured execution windows - comes from parent controller, since windows may be configured to overlap
  // and the logic should really be in one place
  public windows: IExecutionWindow[];

  // observable called in parent controller whenever the user changes an execution window or day restrictions
  public windowsUpdated: Subject<IExecutionWindow[]>;

  public stage: any;

  public chartOptions: IChartOptions;
  public chartData: IChartData;

  // convenience flag based on settings
  public atlasEnabled = false;

  // Used to determine how tall to make the execution window bars - it's just the max of the SPS data
  private maxCount = 0;

  public static $inject = ['$http', '$filter'];
  public constructor(private $http: ng.IHttpService, private $filter: any) {}

  public buildGraph(): void {
    if (!this.stage.restrictedExecutionWindow.atlasEnabled) {
      return;
    }
    this.chartData.loading = true;
    this.$http({ method: 'GET', url: this.getAtlasUrl(), cache: true })
      .then(resp => resp.data)
      .then((data: any) => {
        this.maxCount = 0;
        this.chartOptions.series.length = 1;
        this.chartData.SPS.length = 0;
        this.chartData.windows.length = 0;
        const metadata = data.find((e: any) => e.type === 'graph-metadata');
        if (!metadata) {
          return;
        }
        const timeseries = data.filter(
          (e: any) => e.type === 'timeseries' && e.data.values.some((v: any) => v !== 'NaN'),
        );
        timeseries.forEach((series: any) => {
          const datapoints = series.data.values
            .filter((v: any) => !isNaN(v))
            .map((val: any, idx2: number) => {
              this.maxCount = Math.max(this.maxCount, val);
              return {
                val: Math.round(val),
                timestamp: new Date(metadata.startTime + metadata.step * idx2),
              };
            });
          datapoints.unshift({
            val: 0,
            timestamp: new Date(metadata.startTime - metadata.step),
          });
          this.chartData.SPS = datapoints;
          this.chartOptions.series.push({
            axis: 'y',
            dataset: 'SPS',
            key: 'val',
            color: '#' + series.color.substring(2),
            type: ['area'],
            id: 'SPS',
          });
        });
        this.chartData.loading = false;
        this.addWindowsToChart();
      });
  }

  public toggleEnabled(): void {
    if (this.stage.restrictedExecutionWindow.atlasEnabled) {
      this.stage.restrictedExecutionWindow.currentRegion = this.getDefaultRegion();
      this.buildGraph();
    }
  }

  public $onInit(): void {
    this.atlasEnabled = has(SETTINGS, 'executionWindow.atlas');
    if (!this.atlasEnabled) {
      return;
    }
    this.windowsUpdated.subscribe((newWindows: IExecutionWindow[]) => this.addWindowsToChart(newWindows));
    this.chartData = {
      windows: [],
      SPS: [],
      loading: true,
    };

    this.chartOptions = {
      tooltipHook: rows => {
        if (!rows) {
          return null;
        }
        return {
          abscissas: this.$filter('timestamp')(rows[0].row.x.getTime()),
          rows: rows
            .filter(r => r.row.y1)
            .map(row => {
              if (row.series.dataset === 'windows') {
                return {
                  label: '(in selected window)',
                  value: '',
                  color: row.series.color,
                  id: row.series.id,
                };
              } else {
                return {
                  label: 'SPS',
                  value: row.row.y1,
                  color: row.series.color,
                  id: row.series.id,
                };
              }
            }),
        };
      },
      series: [
        {
          axis: 'y',
          dataset: 'windows',
          key: 'val',
          color: 'green',
          type: ['area'],
          id: 'Selected Execution Windows',
        },
      ],
      axes: {
        x: { key: 'timestamp', type: 'date', ticks: 6 },
        y: { ticks: 3, padding: { min: 0, max: 4 } },
        x2: { ticks: 0 },
        y2: { ticks: 0 },
      },
      zoom: {
        x: true,
        y: true,
      },
    };

    this.regions = SETTINGS.executionWindow.atlas.regions;
    this.buildGraph();
  }

  private getDefaultRegion(): string {
    const deployedRegions = new Set<string>();
    if (this.stage.clusters) {
      this.stage.clusters.forEach((c: any) =>
        Object.keys(c.availabilityZones).forEach((r: string) => deployedRegions.add(r)),
      );
      if (deployedRegions.size === 1 && this.regions.some(r => deployedRegions.has(r.label))) {
        return deployedRegions.values().next().value;
      }
    }
    return this.regions[0].label;
  }

  private addWindowsToChart(newWindows: IExecutionWindow[] = this.windows || []): void {
    if (!this.chartData || this.chartData.loading) {
      return;
    }

    this.chartData.windows.length = 0;

    const windows: IWindowData[] = [];
    const today = new Date();
    const restrictedDays = this.stage.restrictedExecutionWindow.days as number[];
    const days = restrictedDays ? restrictedDays.map(d => (today.getDay() + d) % 7) : [0, 1, 2, 3, 4, 5, 6];
    if (days.includes(0)) {
      days.push(7); // in case there are windows later today
    }

    days.forEach(dayOffset => {
      newWindows.forEach(window => {
        windows.push(this.createWindow(window, dayOffset));
      });
    });

    // build windows based on chart data
    this.chartData.SPS.forEach((datem: IChartPoint) => {
      const ts = datem.timestamp;
      const inWindow = windows.some(w => w.start <= ts.getTime() && w.end >= ts.getTime());
      this.chartData.windows.push({
        val: inWindow ? this.maxCount * 1.1 : 0,
        timestamp: ts,
      });
    });
  }

  private createWindow(window: IExecutionWindow, dayOffset: number): IWindowData {
    const zone: string = SETTINGS.defaultTimeZone;
    const { displayEnd, displayStart } = window;

    const start = DateTime.local()
        .setZone(zone)
        .set({
          hour: displayStart.getHours(),
          minute: displayStart.getMinutes(),
          second: displayStart.getSeconds(),
          millisecond: displayStart.getMilliseconds(),
        })
        .minus(Duration.fromObject({ days: dayOffset }))
        .toMillis(),
      end = DateTime.local()
        .setZone(zone)
        .set({
          hour: displayEnd.getHours(),
          minute: displayEnd.getMinutes(),
          second: displayEnd.getSeconds(),
          millisecond: displayEnd.getMilliseconds(),
        })
        .minus(Duration.fromObject({ days: dayOffset }))
        .toMillis();

    return { start, end };
  }

  private getAtlasUrl(): string {
    const base: string = SETTINGS.executionWindow.atlas.regions.find(
      (r: IAtlasRegion) => r.label === this.stage.restrictedExecutionWindow.currentRegion,
    ).baseUrl;
    return base + SETTINGS.executionWindow.atlas.url;
  }
}

const atlasGraphComponent: ng.IComponentOptions = {
  bindings: {
    windows: '=',
    windowsUpdated: '<',
    stage: '<',
  },
  controller: ExecutionWindowAtlasGraphController,
  template: `
    <div class="form-group" ng-if="$ctrl.atlasEnabled">
      <div class="col-md-9 col-md-offset-1">
        <div class="checkbox">
          <label>
            <input type="checkbox" ng-false-value="undefined"
                   ng-model="$ctrl.stage.restrictedExecutionWindow.atlasEnabled"
                   ng-change="$ctrl.toggleEnabled()"/>
            <strong> Show SPS graphs</strong>
          </label>
        </div>
      </div>
    </div>
    <div ng-if="$ctrl.stage.restrictedExecutionWindow.atlasEnabled">
      <div class="form-group" style="margin-top: 20px">
        <div class="col-md-3 col-md-offset-1">
          Region
          <select class="input-sm" ng-model="$ctrl.stage.restrictedExecutionWindow.currentRegion"
                  ng-options="region.label as region.label for region in $ctrl.regions"
                  ng-change="$ctrl.buildGraph()"></select>
        </div>
        <div class="col-md-6 text-right">
          <div class="small help-contents">
            <b>Zoom:</b> alt key + drag cursor. Double-click: zoom back out.
          </div>
        </div>
      </div>
      <div class="form-group">
        <div class="col-md-10 col-md-offset-1" style="height: 150px">
          <div class="no-data-overlay" ng-if="$ctrl.chartData.loading || $ctrl.chartData.noData">
            <h5 class="text-center">
              <span ng-if="$ctrl.chartData.loading">
                <loading-spinner size="'medium'"></loading-spinner>
              </span>
            </h5>
          </div>
          <linechart data="$ctrl.chartData" options="$ctrl.chartOptions"></linechart>
        </div>
      </div>
    </div>
  `,
};

export const EXECUTION_WINDOW_ATLAS_GRAPH = 'spinnaker.core.pipeline.config.executionWindow.atlas.graph';

module(EXECUTION_WINDOW_ATLAS_GRAPH, []).component('executionWindowAtlasGraph', atlasGraphComponent);
