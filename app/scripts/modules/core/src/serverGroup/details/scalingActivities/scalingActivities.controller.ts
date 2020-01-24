import { IController, module } from 'angular';
import _ from 'lodash';
import { IModalServiceInstance } from 'angular-ui-bootstrap';

import { ServerGroupReader } from '../../serverGroupReader.service';
import { IServerGroup } from 'core/domain';

export interface IScalingActivitiesViewState {
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

export class ScalingActivitiesCtrl implements IController {
  public viewState: IScalingActivitiesViewState;
  public activities: IScalingEventSummary[] = [];

  public static $inject = ['$uibModalInstance', 'serverGroup'];
  public constructor(private $uibModalInstance: IModalServiceInstance, public serverGroup: IServerGroup) {
    this.serverGroup = serverGroup;
  }

  private groupActivities(activities: IRawScalingActivity[]): void {
    const grouped: any = _.groupBy(activities, 'cause');
    const results: IScalingEventSummary[] = [];

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
          events.push({ description: entry.description, availabilityZone });
        });
        results.push({
          cause: group[0].cause,
          events,
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
    ServerGroupReader.getScalingActivities(this.serverGroup).then(
      (activities: IRawScalingActivity[]) => {
        this.viewState.loading = false;
        this.groupActivities(activities);
      },
      () => {
        this.viewState.error = true;
      },
    );
  }

  public close(): void {
    this.$uibModalInstance.close();
  }
}

export const SCALING_ACTIVITIES_CTRL = 'spinnaker.core.serverGroup.scalingActivities.controller';
module(SCALING_ACTIVITIES_CTRL, []).controller('ScalingActivitiesCtrl', ScalingActivitiesCtrl);
