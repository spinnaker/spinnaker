import { shallow } from 'enzyme';
import React from 'react';

import { HelpField } from '@spinnaker/core';

import {
  GceInstanceFlexibilityConfigurer,
  hasValidFlexibilityPolicy,
  nextSelectionName,
} from './GceInstanceFlexibilityConfigurer';

describe('GceInstanceFlexibilityConfigurer', () => {
  const policy = {
    instanceSelections: {
      preferred: { rank: 1, machineTypes: ['n2-standard-8'] },
    },
  };

  it('links the flexibility editor to its help content', () => {
    const wrapper = shallow(
      <GceInstanceFlexibilityConfigurer
        regional={true}
        targetShape="BALANCED"
        setInstanceFlexibilityPolicy={jasmine.createSpy('setPolicy')}
      />,
    );

    expect(wrapper.find(HelpField).prop('id')).toBe('gce.serverGroup.instanceFlexibilityPolicy');
  });

  it('allows EVEN when flexibility is absent', () => {
    expect(
      hasValidFlexibilityPolicy({
        regional: true,
        distributionPolicy: { targetShape: 'EVEN' },
      }),
    ).toBe(true);
  });

  it('blocks zonal and EVEN combinations when selections exist', () => {
    const policy = {
      instanceSelections: {
        preferred: { machineTypes: ['n2-standard-8'] },
      },
    };
    expect(
      hasValidFlexibilityPolicy({
        regional: false,
        distributionPolicy: { targetShape: 'BALANCED' },
        instanceFlexibilityPolicy: policy,
      }),
    ).toBe(false);
    expect(
      hasValidFlexibilityPolicy({
        regional: true,
        distributionPolicy: { targetShape: 'EVEN' },
        instanceFlexibilityPolicy: policy,
      }),
    ).toBe(false);
  });

  it('rejects blank machine type placeholders', () => {
    expect(
      hasValidFlexibilityPolicy({
        regional: true,
        distributionPolicy: { targetShape: 'BALANCED' },
        instanceFlexibilityPolicy: {
          instanceSelections: {
            preferred: { machineTypes: [''] },
          },
        },
      }),
    ).toBe(false);
    expect(
      hasValidFlexibilityPolicy({
        regional: true,
        distributionPolicy: { targetShape: 'BALANCED' },
        instanceFlexibilityPolicy: {
          instanceSelections: {
            preferred: { machineTypes: ['n2-standard-8', ''] },
          },
        },
      }),
    ).toBe(false);
  });

  it('rejects duplicate machine types within and across selections', () => {
    [
      {
        preferred: { machineTypes: ['e2-standard-2', 'e2-standard-2'] },
      },
      {
        preferred: { machineTypes: ['e2-standard-2'] },
        fallback: { machineTypes: ['e2-standard-2'] },
      },
    ].forEach((instanceSelections) => {
      expect(
        hasValidFlexibilityPolicy({
          regional: true,
          distributionPolicy: { targetShape: 'BALANCED' },
          instanceFlexibilityPolicy: { instanceSelections },
        }),
      ).toBe(false);
    });
  });

  it('normalizes duplicate machine types and displays a warning', () => {
    const duplicatePolicy = {
      instanceSelections: {
        preferred: {
          machineTypes: [
            ' https://www.googleapis.com/compute/v1/projects/test-project/zones/us-central1-a/machineTypes/E2-STANDARD-2 ',
          ],
        },
        fallback: { machineTypes: ['zones/us-central1-b/machineTypes/e2-standard-2'] },
      },
    };

    expect(
      hasValidFlexibilityPolicy({
        regional: true,
        distributionPolicy: { targetShape: 'BALANCED' },
        instanceFlexibilityPolicy: duplicatePolicy,
      }),
    ).toBe(false);

    const wrapper = shallow(
      <GceInstanceFlexibilityConfigurer
        instanceFlexibilityPolicy={duplicatePolicy}
        regional={true}
        targetShape="BALANCED"
        setInstanceFlexibilityPolicy={jasmine.createSpy('setPolicy')}
      />,
    );
    expect(wrapper.text()).toContain('Machine types must be unique across instance selections.');
    expect(wrapper.find('[role="alert"]').text()).toBe('Machine types must be unique across instance selections.');
  });

  it('accepts distinct machine types across selections', () => {
    expect(
      hasValidFlexibilityPolicy({
        regional: true,
        distributionPolicy: { targetShape: 'BALANCED' },
        instanceFlexibilityPolicy: {
          instanceSelections: {
            preferred: { machineTypes: ['e2-standard-2', 'n2-standard-2'] },
            fallback: { machineTypes: ['c2-standard-4'] },
          },
        },
      }),
    ).toBe(true);
  });

  it('rejects ranks that are not finite non-negative integers', () => {
    [-1, 1.5, Number.NaN, Number.POSITIVE_INFINITY].forEach((rank) => {
      expect(
        hasValidFlexibilityPolicy({
          regional: true,
          distributionPolicy: { targetShape: 'BALANCED' },
          instanceFlexibilityPolicy: {
            instanceSelections: {
              preferred: { rank, machineTypes: ['n2-standard-8'] },
            },
          },
        }),
      ).toBe(false);
    });
  });

  it('announces submission errors and associates malformed persisted controls', () => {
    const wrapper = shallow(
      <GceInstanceFlexibilityConfigurer
        instanceFlexibilityPolicy={{
          instanceSelections: {
            preferred: { rank: -1, machineTypes: ['', 'n2-standard-8'] },
          },
        }}
        regional={true}
        targetShape="BALANCED"
        validationError="Instance flexibility policy is invalid."
        setInstanceFlexibilityPolicy={jasmine.createSpy('setPolicy')}
      />,
    );
    const errorId = 'gce-instance-flexibility-error';
    const rankInput = wrapper.find('#instance-flexibility-selection-preferred-rank');
    const blankMachineType = wrapper.find('input[aria-label="Machine type 1 for selection preferred"]');
    const validMachineType = wrapper.find('input[aria-label="Machine type 2 for selection preferred"]');

    expect(wrapper.find(`#${errorId}`).prop('role')).toBe('alert');
    expect(wrapper.find(`#${errorId}`).text()).toBe('Instance flexibility policy is invalid.');
    expect(rankInput.prop('aria-invalid')).toBe(true);
    expect(rankInput.prop('aria-describedby')).toBe(errorId);
    expect(blankMachineType.prop('aria-invalid')).toBe(true);
    expect(blankMachineType.prop('aria-describedby')).toBe(errorId);
    expect(validMachineType.prop('aria-invalid')).toBe(false);
    expect(validMachineType.prop('aria-describedby')).toBeUndefined();
  });

  it('accepts regional BALANCED/ANY/ANY_SINGLE_ZONE with rankless selections', () => {
    const policy = {
      instanceSelections: {
        preferred: { machineTypes: ['n2-standard-8'] },
        fallback: { machineTypes: ['e2-standard-8'] },
      },
    };
    ['BALANCED', 'ANY', 'ANY_SINGLE_ZONE', ' balanced '].forEach((targetShape) => {
      expect(
        hasValidFlexibilityPolicy({
          regional: true,
          distributionPolicy: { targetShape },
          instanceFlexibilityPolicy: policy,
        }),
      ).toBe(true);
    });
  });

  it('chooses the first unused selection name', () => {
    expect(nextSelectionName([])).toBe('selection-1');
    expect(nextSelectionName(['selection-1', 'preferred'])).toBe('selection-2');
    expect(nextSelectionName(['selection-1', 'selection-2'])).toBe('selection-3');
    expect(nextSelectionName(['selection-2'])).toBe('selection-1');
  });

  it('adds a named selection through the configurer', () => {
    const setPolicy = jasmine.createSpy('setPolicy');
    const wrapper = shallow(
      <GceInstanceFlexibilityConfigurer
        instanceFlexibilityPolicy={undefined}
        regional={true}
        targetShape="BALANCED"
        setInstanceFlexibilityPolicy={setPolicy}
      />,
    );

    wrapper.find('button').simulate('click');

    expect(setPolicy).toHaveBeenCalledWith({
      instanceSelections: {
        'selection-1': { machineTypes: [''] },
      },
    });
  });

  it('does not overwrite an existing renamed selection when adding another', () => {
    const setPolicy = jasmine.createSpy('setPolicy');
    const wrapper = shallow(
      <GceInstanceFlexibilityConfigurer
        instanceFlexibilityPolicy={{
          instanceSelections: {
            'selection-1': { machineTypes: ['n2-standard-8'] },
          },
        }}
        regional={true}
        targetShape="BALANCED"
        setInstanceFlexibilityPolicy={setPolicy}
      />,
    );

    wrapper.find('button').last().simulate('click');

    expect(setPolicy).toHaveBeenCalledWith({
      instanceSelections: {
        'selection-1': { machineTypes: ['n2-standard-8'] },
        'selection-2': { machineTypes: [''] },
      },
    });
  });

  it('sends an explicit empty policy when the final selection is removed', () => {
    const setPolicy = jasmine.createSpy('setPolicy');
    const wrapper = shallow(
      <GceInstanceFlexibilityConfigurer
        instanceFlexibilityPolicy={policy}
        regional={true}
        targetShape="BALANCED"
        setInstanceFlexibilityPolicy={setPolicy}
      />,
    );

    wrapper
      .find('button')
      .filterWhere((button) => button.text() === 'Remove')
      .simulate('click');

    expect(setPolicy).toHaveBeenCalledWith({ instanceSelections: {} });
  });

  it('only persists finite non-negative integer ranks and supports rank zero and clearing', () => {
    const setPolicy = jasmine.createSpy('setPolicy');
    const wrapper = shallow(
      <GceInstanceFlexibilityConfigurer
        instanceFlexibilityPolicy={policy}
        regional={true}
        targetShape="BALANCED"
        setInstanceFlexibilityPolicy={setPolicy}
      />,
    );
    const rankInput = wrapper.find('input[type="number"]');

    expect(rankInput.prop('step')).toBe(1);

    rankInput.simulate('change', { target: { value: '1.5' } });
    rankInput.simulate('change', { target: { value: '-1' } });
    rankInput.simulate('change', { target: { value: 'not-a-number' } });
    expect(setPolicy).not.toHaveBeenCalled();

    rankInput.simulate('change', { target: { value: '0' } });
    expect(setPolicy).toHaveBeenCalledWith({
      instanceSelections: {
        preferred: { rank: 0, machineTypes: ['n2-standard-8'] },
      },
    });

    setPolicy.calls.reset();
    rankInput.simulate('change', { target: { value: '' } });
    expect(setPolicy).toHaveBeenCalledWith({
      instanceSelections: {
        preferred: { machineTypes: ['n2-standard-8'] },
      },
    });
  });

  it('resets blank and duplicate rename drafts to the current valid selection name', () => {
    const setPolicy = jasmine.createSpy('setPolicy');
    const wrapper = shallow(
      <GceInstanceFlexibilityConfigurer
        instanceFlexibilityPolicy={{
          instanceSelections: {
            preferred: { machineTypes: ['n2-standard-8'] },
            fallback: { machineTypes: ['e2-standard-8'] },
          },
        }}
        regional={true}
        targetShape="BALANCED"
        setInstanceFlexibilityPolicy={setPolicy}
      />,
    );
    const preferredNameInput = wrapper.find('input').filterWhere((input) => input.prop('defaultValue') === 'preferred');
    const blankDraft = { value: ' ' };
    const duplicateDraft = { value: 'fallback' };

    preferredNameInput.simulate('blur', { currentTarget: blankDraft, target: blankDraft });
    expect(blankDraft.value).toBe('preferred');
    preferredNameInput.simulate('blur', { currentTarget: duplicateDraft, target: duplicateDraft });
    expect(duplicateDraft.value).toBe('preferred');
    expect(setPolicy).not.toHaveBeenCalled();
  });

  it('associates labels and contextual accessible names with selection controls', () => {
    const wrapper = shallow(
      <GceInstanceFlexibilityConfigurer
        instanceFlexibilityPolicy={policy}
        regional={true}
        targetShape="BALANCED"
        setInstanceFlexibilityPolicy={jasmine.createSpy('setPolicy')}
      />,
    );
    const selectionId = 'instance-flexibility-selection-preferred';

    expect(wrapper.find(`label[htmlFor="${selectionId}-name"]`).text()).toBe('Selection name');
    expect(wrapper.find(`input#${selectionId}-name`).exists()).toBe(true);
    expect(wrapper.find(`label[htmlFor="${selectionId}-rank"]`).text()).toBe('Rank (optional)');
    expect(wrapper.find(`input#${selectionId}-rank`).exists()).toBe(true);
    expect(wrapper.find(`input[aria-label="Machine type 1 for selection preferred"]`).prop('id')).toBe(
      `${selectionId}-machine-type-0`,
    );
    expect(wrapper.find('button[aria-label="Remove selection preferred"]').exists()).toBe(true);
    expect(wrapper.find('button[aria-label="Remove machine type 1 from selection preferred"]').exists()).toBe(true);
  });

  it('rerenders when policy and sibling primitive props change identity', () => {
    const wrapper = shallow(
      <GceInstanceFlexibilityConfigurer
        instanceFlexibilityPolicy={undefined}
        regional={true}
        targetShape="BALANCED"
        setInstanceFlexibilityPolicy={jasmine.createSpy('setPolicy')}
      />,
    );

    expect(wrapper.text()).toContain('Add flexibility policy');

    wrapper.setProps({ instanceFlexibilityPolicy: policy });
    expect(wrapper.find('button[aria-label="Remove selection preferred"]').exists()).toBe(true);

    wrapper.setProps({ regional: false, targetShape: 'EVEN' });
    expect(wrapper.text()).toContain('Flexibility requires a regional server group.');
    expect(wrapper.text()).toContain('not EVEN');
  });
});
