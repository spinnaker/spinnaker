import { module } from 'angular';
import { Arc, arc, DefaultArcObject, Pie, pie } from 'd3-shape';

import './availability.less';

interface ArcData {
  path: string;
  score: number;
}

interface IDonutGraphData {
  arcs: ArcData[];
  total: string;
  width: number;
  height: number;
}

export class AvailabilityDonutController implements ng.IComponentController {
  public availability: number;
  public donut: IDonutGraphData;
  public ninesSize: number;
  public percentSize: number;
  public score: number;
  public displayNines: string;
  private nines: number;
  private targetNines: number;
  private outerRadius: number;
  private arc: Arc<any, DefaultArcObject> = arc();
  private pie: Pie<any, number> = pie<number>().sort(null).sortValues(null);
  private donutWidthPercent = 0.7;

  private updateData(): void {
    if (this.targetNines && this.nines && this.outerRadius) {
      this.donut = this.buildDonutGraph();
      this.ninesSize = this.outerRadius * 0.46;
      this.percentSize = this.outerRadius * 0.2;
      this.displayNines = (this.nines >= 7) ? `${this.nines}+` : String(this.nines);
    }
  }

  public $onInit(): void {
    this.updateData();
  }

  public $onChanges(): void {
    this.updateData();
  }

  private buildDonutGraph(): IDonutGraphData {
    const totalNines = Math.min(this.nines, this.targetNines);
    const pieData = [totalNines, this.targetNines - totalNines];
    const availabilityPie = this.pie(pieData);
    const arcs: ArcData[] = [
      // Availability arc
      {
        path: this.arc({
          startAngle: availabilityPie[0].startAngle,
          endAngle: availabilityPie[0].endAngle,
          innerRadius: this.outerRadius * this.donutWidthPercent,
          outerRadius: this.outerRadius,
          padAngle: 0
        }),
        score: this.score
      },
      // Empty arc
      {
        path: this.arc({
          startAngle: availabilityPie[1].startAngle,
          endAngle: availabilityPie[1].endAngle,
          innerRadius: this.outerRadius * this.donutWidthPercent,
          outerRadius: this.outerRadius,
          padAngle: 0
        }),
        score: 0
      }
    ];

    // Total donut (needed for a clean border)
    // Build a donut graph with one slice for the availability and one slice
    // for the empty space, then create a separate whole donut to fake a border.
    // If we add strokes to the paths, then there's a stroke on the connecting sides,
    // which we don't want. If I create a donut with the empty color that fills the
    // whole donut, then give _that_ a stroke, anti-aliasing ruins the border near
    // the availability score.
    const total = this.arc({
      startAngle: 0,
      endAngle: 2 * Math.PI,
      innerRadius: this.outerRadius * this.donutWidthPercent - 2,
      outerRadius: this.outerRadius + 2,
      padAngle: 0
    });

    return {
      arcs: arcs,
      total: total,
      width: this.outerRadius * 2.5,
      height: this.outerRadius * 2.5
    };
  }
}

class AvailabilityDonutComponent implements ng.IComponentOptions {
  public bindings: any = {
    availability: '<',
    nines: '<',
    targetNines: '<',
    outerRadius: '<',
    score: '<'
  };

  public controller: any = AvailabilityDonutController;
  public templateUrl: string = require('./availability.donut.html');
}

export const AVAILABILITY_DONUT_COMPONENT = 'spinnaker.netflix.availability.donut.component';

module(AVAILABILITY_DONUT_COMPONENT, [])
.component('availabilityDonut', new AvailabilityDonutComponent());
