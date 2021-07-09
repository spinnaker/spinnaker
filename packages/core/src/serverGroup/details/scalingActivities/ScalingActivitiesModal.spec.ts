import { groupScalingActivities, IScalingEventSummary } from './ScalingActivitiesModal';

describe('Group Scaling Activities', () => {
  it('groups activities by cause, parsing availability zone from details, sorted by start date, newest first', () => {
    const activities = [
      {
        description: 'Launching a new EC2 instance: i-05c487e8',
        details: '{"Availability Zone":"us-east-1d"}',
        cause: 'common cause',
        statusCode: 'Successful',
        startTime: 3,
      },
      {
        description: 'Launching a new EC2 instance: i-abcdefgh',
        details: '{"Availability Zone":"us-east-1e"}',
        cause: 'common cause',
        statusCode: 'Successful',
        startTime: 3,
      },
      {
        description: 'some other thing',
        details: '{"Availability Zone":"us-east-1c"}',
        cause: 'some other cause',
        statusCode: 'Successful',
        startTime: 2,
      },
    ];

    const result: IScalingEventSummary[] = groupScalingActivities(activities);
    expect(result.length).toEqual(2);
    expect(result[0].cause).toBe('common cause');
    expect(result[1].cause).toBe('some other cause');
    expect(result[0].events[0].description).toEqual(activities[0].description);
    expect(result[0].events[1].description).toEqual(activities[1].description);
    expect(result[0].events[0].availabilityZone).toBe('us-east-1d');
    expect(result[0].events[1].availabilityZone).toBe('us-east-1e');
  });

  it('returns "unknown" for availability zone if details field not present, cannot be parsed, or does not contain key "Availability Zone"', () => {
    const activities = [
      {
        description: 'a',
        details: 'not JSON so cannot be parsed',
        cause: 'some cause',
        statusCode: 'z',
        startTime: 1,
      },
      {
        description: 'b',
        details: null,
        cause: 'some cause',
        statusCode: 'z',
        startTime: 1,
      },
      {
        description: 'c',
        details: '{"Not Availability Zone":"us-east-1c"}',
        cause: 'some cause',
        statusCode: 'z',
        startTime: 1,
      },
      {
        description: 'c',
        details: '{"Availability Zone":"us-east-1c"}',
        cause: 'some cause',
        statusCode: 'z',
        startTime: 1,
      },
    ];

    const result: IScalingEventSummary[] = groupScalingActivities(activities);
    expect(result.length).toEqual(1);
    expect(result[0].cause).toBe('some cause');
    expect(result[0].events[0].availabilityZone).toBe('unknown');
    expect(result[0].events[1].availabilityZone).toBe('unknown');
    expect(result[0].events[2].availabilityZone).toBe('unknown');
    expect(result[0].events[3].availabilityZone).toBe('us-east-1c');
  });
});
