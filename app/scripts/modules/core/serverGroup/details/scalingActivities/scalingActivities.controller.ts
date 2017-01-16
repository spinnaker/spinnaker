import * as _ from 'lodash';
import {module} from 'angular';
import {IModalServiceInstance} from '../../../../../../types/angular-ui-bootstrap';

import {SERVER_GROUP_READER, ServerGroupReader} from 'core/serverGroup/serverGroupReader.service';
import {ServerGroup} from 'core/domain';

interface IViewState {
  loading: boolean;
  error: boolean;
}

export interface IScalingEvent {
  description: string;
  availabilityZone: string;
}

export interface IScalingEventSummary {
  cause: string;
  events: IScalingEvent[];
  startTime: number;
  statusCode: string;
  isSuccessful: boolean;
}

export interface IRawScalingActivity {
  details: string;
  description: string;
  cause: string;
  statusCode: string;
  startTime: number;
}

export class ScalingActivitiesCtrl implements ng.IComponentController {
  public viewState: IViewState;
  public activities: IScalingEventSummary[] = [];

  static get $inject() { return ['$uibModalInstance', 'serverGroupReader', 'serverGroup']; }

  public constructor(private $uibModalInstance: IModalServiceInstance,
                     private serverGroupReader: ServerGroupReader,
                     public serverGroup: ServerGroup) {
    this.serverGroup = serverGroup;
  }

  private groupActivities(activities: IRawScalingActivity[]): void {
    const grouped: any = _.groupBy(activities, 'cause'),
      results: IScalingEventSummary[] = [];

    _.forOwn(grouped, (group: IRawScalingActivity[]) => {
      if (group.length) {
        const events: IScalingEvent[] = [];
        group.forEach((entry: any) => {
          let availabilityZone = 'unknown';
          try {
            availabilityZone = JSON.parse(entry.details)['Availability Zone'] || availabilityZone;
          } catch (e) {
            // I don't imagine this would happen but let's not blow up the world if it does.
          }
          events.push({description: entry.description, availabilityZone: availabilityZone});
        });
        results.push({
          cause: group[0].cause,
          events: events,
          startTime: group[0].startTime,
          statusCode: group[0].statusCode,
          isSuccessful: group[0].statusCode === 'Successful',
        });
      }
    });
    this.activities = _.sortBy(results, 'startTime').reverse();
  }

  public $onInit(): void {
    this.viewState = {
      loading: true,
      error: false,
    };
    this.serverGroupReader.getScalingActivities(this.serverGroup)
      .then(
        (activities: IRawScalingActivity[]) => {
          this.viewState.loading = false;
          this.groupActivities(activities);
        },
        () => {
          this.viewState.error = true;
        }
      );
  }

  public close(): void {
    this.$uibModalInstance.close();
  }
}

export const SCALING_ACTIVITIES_CTRL = 'spinnaker.core.serverGroup.scalingActivities.controller';
module(SCALING_ACTIVITIES_CTRL, [SERVER_GROUP_READER])
  .controller('ScalingActivitiesCtrl', ScalingActivitiesCtrl);
