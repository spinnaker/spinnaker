import React from 'react';
import { mount } from 'enzyme';

import type { IStage } from '../../../../domain';
import { JsonEditor } from '../../../../presentation';
import { UnmatchedStageTypeStageConfig } from './UnmatchedStageTypeStageConfig';

describe('UnmatchedStageTypeStageConfig', () => {
  const mountEditor = (stage: IStage, stageFieldUpdated = jasmine.createSpy('stageFieldUpdated')) =>
    mount((<UnmatchedStageTypeStageConfig stage={stage} stageFieldUpdated={stageFieldUpdated} />) as any);

  it('hides common fields that are edited by the direct React page', () => {
    const component = mountEditor({
      refId: '1',
      requisiteStageRefIds: ['0'],
      failPipeline: true,
      comments: 'keep me outside JSON',
      name: 'Unknown stage',
      type: 'customStage',
      customField: 'visible',
    } as any);

    const editorValue = component.find(JsonEditor).prop('value') as string;

    expect(editorValue).toContain('customField');
    expect(editorValue).toContain('customStage');
    expect(editorValue).not.toContain('refId');
    expect(editorValue).not.toContain('requisiteStageRefIds');
    expect(editorValue).not.toContain('failPipeline');
    expect(editorValue).not.toContain('comments');
    expect(editorValue).not.toContain('Unknown stage');
  });

  it('updates editable fields while preserving common fields', () => {
    const stageFieldUpdated = jasmine.createSpy('stageFieldUpdated');
    const stage = {
      refId: '1',
      requisiteStageRefIds: ['0'],
      comments: 'keep me outside JSON',
      name: 'Unknown stage',
      type: 'customStage',
      customField: 'old',
      removedField: true,
    } as any;
    const component = mountEditor(stage, stageFieldUpdated);

    component.find(JsonEditor).prop('onChange')('{"type":"customStage","customField":"new"}');

    expect(stage).toEqual({
      refId: '1',
      requisiteStageRefIds: ['0'],
      comments: 'keep me outside JSON',
      name: 'Unknown stage',
      type: 'customStage',
      customField: 'new',
    } as any);
    expect(stageFieldUpdated).toHaveBeenCalled();
  });

  it('rejects invalid JSON without updating the stage', () => {
    const stageFieldUpdated = jasmine.createSpy('stageFieldUpdated');
    const stage = { type: 'customStage', customField: 'old' } as any;
    const component = mountEditor(stage, stageFieldUpdated);

    component.find(JsonEditor).prop('onChange')('{');
    component.update();

    expect(stage.customField).toBe('old');
    expect(stageFieldUpdated).not.toHaveBeenCalled();
    expect(component.text()).toContain('Error:');
  });

  it('rejects deleting type without updating the stage', () => {
    const stageFieldUpdated = jasmine.createSpy('stageFieldUpdated');
    const stage = { type: 'customStage', customField: 'old' } as any;
    const component = mountEditor(stage, stageFieldUpdated);

    component.find(JsonEditor).prop('onChange')('{"customField":"new"}');
    component.update();

    expect(stage).toEqual({ type: 'customStage', customField: 'old' } as any);
    expect(stageFieldUpdated).not.toHaveBeenCalled();
    expect(component.text()).toContain('Cannot delete property type.');
  });
});
