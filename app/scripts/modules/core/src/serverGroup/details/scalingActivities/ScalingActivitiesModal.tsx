import { forOwn, groupBy, sortBy } from 'lodash';
import * as React from 'react';

import { IServerGroup } from '../../../domain';
import { IModalComponentProps, ModalBody, ModalFooter, ModalHeader, useData } from '../../../presentation';
import { ServerGroupReader } from '../../serverGroupReader.service';
import { timestamp } from '../../../utils';
import { Spinner } from '../../../widgets';

import './ScalingActivitiesModal.less';

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

export interface IScalingActivitiesModalProps extends IModalComponentProps {
  serverGroup: IServerGroup;
}

export const groupScalingActivities = (activities: IRawScalingActivity[]): IScalingEventSummary[] => {
  const grouped = groupBy(activities, 'cause');
  const results: IScalingEventSummary[] = [];

  forOwn(grouped, (group: IRawScalingActivity[]) => {
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

  return sortBy(results, 'startTime').reverse();
};

export const ScalingActivitiesModal = ({ dismissModal, serverGroup }: IScalingActivitiesModalProps) => {
  const fetchScalingActivities = () =>
    ServerGroupReader.getScalingActivities(serverGroup).then((a) => groupScalingActivities(a));

  const { result: scalingActivities, status, error } = useData(fetchScalingActivities, [], [serverGroup.name]);
  const loading = status === 'PENDING';

  return (
    <>
      <ModalHeader>{`Scaling Activities for ${serverGroup.name}`}</ModalHeader>
      <ModalBody>
        {loading && (
          <div className="ScalingAcivitiesModalBody middle center sp-margin-xl-yaxis">
            <Spinner />
          </div>
        )}
        {!loading && Boolean(error) && (
          <div className="ScalingAcivitiesModalBody middle sp-margin-xl-yaxis">
            <p>{`There was an error loading scaling activities for ${serverGroup.name}. Please try again later.`}</p>
          </div>
        )}
        {!loading && !error && !scalingActivities.length && (
          <div className="ScalingAcivitiesModalBody middle sp-margin-xl-yaxis">
            <p>{`No scaling activities found for ${serverGroup.name}.`}</p>
          </div>
        )}
        {!loading && !error && scalingActivities.length && (
          <div className="ScalingAcivitiesModalBody middle sp-margin-xl-yaxis">
            {scalingActivities.map((a, i) => (
              <div key={a.cause}>
                <p className="clearfix">
                  <span className={`label label-${a.isSuccessful ? 'success' : 'danger'} pull-left`}>
                    {a.statusCode}
                  </span>
                  <span className="label label-default pull-right">{timestamp(a.startTime)}</span>
                </p>
                <p>{a.cause}</p>
                <p>Summary of activities:</p>
                <ul>
                  {sortBy(a.events, 'availabilityZone', 'description').map((e) => (
                    <li key={e.description}>
                      <span className="sp-margin-xs-right">{e.description}</span>
                      {e.availabilityZone && <span>{e.availabilityZone}</span>}
                    </li>
                  ))}
                </ul>
                {i !== scalingActivities.length - 1 && <hr />}
              </div>
            ))}
          </div>
        )}
      </ModalBody>
      <ModalFooter
        primaryActions={
          <button className="btn btn-primary" onClick={dismissModal}>
            Close
          </button>
        }
      />
    </>
  );
};
