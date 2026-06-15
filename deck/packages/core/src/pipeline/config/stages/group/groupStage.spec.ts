import { GroupExecutionLabel } from './GroupExecutionLabel';
import { GroupMarkerIcon } from './GroupMarkerIcon';
import { groupStage } from './groupStage';

describe('Group stage registration', () => {
  it('registers Group as a synthetic React-labelled stage without Angular config fields', () => {
    expect(groupStage).toEqual(
      jasmine.objectContaining({
        description: 'A group of stages',
        executionLabelComponent: GroupExecutionLabel,
        markerIcon: GroupMarkerIcon,
        key: 'group',
        label: 'Group',
        useCustomTooltip: true,
        synthetic: true,
        validators: [],
      }),
    );
    expect((groupStage as any).controller).toBeUndefined();
    expect((groupStage as any).templateUrl).toBeUndefined();
  });
});
