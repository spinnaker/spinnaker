import { module } from 'angular';
import { Arc, arc, DefaultArcObject } from 'd3-shape';
import { scalePow } from 'd3-scale';

import './availability.less';

interface IArcData {
  path: string;
  score: number;
}

interface ITargetData {
  availability: number;
  path: string;
  rotation: number;
  labelPosition: [number, number];
  score: number;
}

interface IDonutGraphData {
  arcs: IArcData[];
  targets: ITargetData[];
  total: string;
  width: number;
  height: number;
}

const maxNines = 4.6;

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
  private donutWidthPercent = 0.7;

  // inflection points for changing the scale of the donut
  private inflectionDomains = [ [0, 2], [2, maxNines] ];
  private inflectionRange = 360 / this.inflectionDomains.length;
  private inflectionRanges = this.inflectionDomains.map((_, i) => [ this.inflectionRange * i, this.inflectionRange * (i + 1) ]);
  private inflectionRangesRadians = this.inflectionRanges.map((range) => [ range[0] * (Math.PI / 180), range[1] * (Math.PI / 180) ]);

  private scaleTargets(target: number, radians = false): number {
    const inflectionIndex = this.inflectionDomains.findIndex((domain) => domain[0] < target && target < domain[1]);
    const inflectionRanges = radians ? this.inflectionRangesRadians : this.inflectionRanges;

    return scalePow().domain(this.inflectionDomains[inflectionIndex]).range(inflectionRanges[inflectionIndex])(target);
  }

  private updateData(): void {
    if (this.nines && this.outerRadius) {
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
    const currentNines = Math.min(this.nines, maxNines);
    const angle = this.scaleTargets(currentNines, true) - 0.02;
    const arcs: IArcData[] = [
      // Availability arc
      {
        path: this.arc({
          startAngle: 0,
          endAngle: angle,
          innerRadius: this.outerRadius * this.donutWidthPercent,
          outerRadius: this.outerRadius,
          padAngle: 0
        }),
        score: this.score
      },
      // Empty arc
      {
        path: this.arc({
          startAngle: angle,
          endAngle: Math.PI * 2,
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

    // Figure out rotations needed for target inflection markers
    const radius = this.outerRadius + 5;
    const tHeight = 8;
    const tWidth = 7;

    const targetArrowPath = `M 0 -${radius} L -${tWidth / 2} -${radius + tHeight} L ${tWidth / 2} -${radius + tHeight} L 0 -${radius}`;
    const targetRotation = this.scaleTargets(this.targetNines);
    const targetUnderRotation = this.scaleTargets(this.targetNines * 0.95);
    const targetAngle = this.scaleTargets(this.targetNines, true) - (Math.PI / 2);
    const targetUnderAngle = this.scaleTargets(this.targetNines * 0.95, true) - (Math.PI / 2);

    const labelRadius = radius + tHeight + 3;
    const targets: ITargetData[] = [
      {
        availability: this.targetNines,
        rotation: targetRotation,
        path: targetArrowPath,
        labelPosition: [
          labelRadius * Math.cos(targetAngle),
          labelRadius * Math.sin(targetAngle)
        ],
        score: 1
      },
      {
        availability: (this.targetNines * 0.95),
        rotation: targetUnderRotation,
        path: targetArrowPath,
        labelPosition: [
          labelRadius * Math.cos(targetUnderAngle),
          labelRadius * Math.sin(targetUnderAngle)
        ],
        score: 2
      }
    ];

    return {
      arcs: arcs,
      total: total,
      targets: targets,
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
