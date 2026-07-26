import { GroupExecutionLabel } from './GroupExecutionLabel';
import { GroupMarkerIcon } from './GroupMarkerIcon';
import { groupStage } from './groupStage';

describe('Group stage registration', () => {
  it('registers Group as a synthetic stage with a React execution label', () => {
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
  });
});
