import { act, cleanup, render, screen } from '@testing-library/react';
import ace from 'ace-builds';
import React from 'react';

import { UnmatchedStageTypeStageConfig } from './UnmatchedStageTypeStageConfig';
import { ApplicationModelBuilder } from '../../../../application';
import type { IStageConfigProps } from '../common';
import type { IPipeline, IStage } from '../../../../domain';

describe('UnmatchedStageTypeStageConfig', () => {
  const createProps = (stage: IStage, stageFieldUpdated = vi.fn()): IStageConfigProps => ({
    application: ApplicationModelBuilder.createApplicationForTests('app'),
    pipeline: { application: 'app', id: 'pipeline-id', name: 'Pipeline', stages: [stage] } as IPipeline,
    stage,
    stageFieldUpdated,
    updateStage: vi.fn(),
    updateStageField: vi.fn(),
  });

  const getEditor = (container: HTMLElement) => ace.edit(container.querySelector('.ace-editor') as HTMLElement);

  beforeEach(() => vi.useFakeTimers());

  afterEach(() => {
    cleanup();
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('renders only fields owned by the unmatched-stage JSON editor', () => {
    const stage = {
      refId: '1',
      requisiteStageRefIds: ['0'],
      failPipeline: true,
      comments: 'keep me outside JSON',
      name: 'Unknown stage',
      type: 'customStage',
      customField: 'visible',
    } as IStage;
    const { container } = render(<UnmatchedStageTypeStageConfig {...createProps(stage)} />);

    const editorValue = getEditor(container).getValue();
    expect(screen.getByRole('form')).toBeVisible();
    expect(editorValue).toContain('customField');
    expect(editorValue).toContain('customStage');
    expect(editorValue).not.toContain('refId');
    expect(editorValue).not.toContain('requisiteStageRefIds');
    expect(editorValue).not.toContain('failPipeline');
    expect(editorValue).not.toContain('comments');
    expect(editorValue).not.toContain('Unknown stage');
  });

  it('updates the same stage through JsonEditor and normalizes hidden fields from the editor value', () => {
    const stage = {
      refId: '1',
      requisiteStageRefIds: ['0'],
      comments: 'keep me outside JSON',
      name: 'Unknown stage',
      type: 'customStage',
      customField: 'old',
      removedField: true,
    } as IStage;
    const originalStage = stage;
    const stageFieldUpdated = vi.fn();
    const { container } = render(<UnmatchedStageTypeStageConfig {...createProps(stage, stageFieldUpdated)} />);
    const editor = getEditor(container);

    act(() => editor.setValue('{"type":"customStage","customField":"new","name":"Unknown stage"}'));

    expect(stage).toBe(originalStage);
    expect(stage).toEqual({
      refId: '1',
      requisiteStageRefIds: ['0'],
      comments: 'keep me outside JSON',
      name: 'Unknown stage',
      type: 'customStage',
      customField: 'new',
    } as IStage);
    expect(JSON.parse(editor.getValue())).toEqual({ type: 'customStage', customField: 'new' });
    expect(stageFieldUpdated).toHaveBeenCalledTimes(1);
    expect(screen.queryByText(/^Error:/)).not.toBeInTheDocument();
  });

  it('shows the exact parse error without updating the stage', () => {
    const stage = { type: 'customStage', customField: 'old' } as IStage;
    const stageFieldUpdated = vi.fn();
    const { container } = render(<UnmatchedStageTypeStageConfig {...createProps(stage, stageFieldUpdated)} />);

    let expectedMessage: string;
    try {
      JSON.parse('{');
    } catch (error) {
      expectedMessage = error.message;
    }

    act(() => getEditor(container).setValue('{'));

    expect(screen.getByText(`Error: ${expectedMessage}`)).toBeVisible();
    expect(stage).toEqual({ type: 'customStage', customField: 'old' } as IStage);
    expect(stageFieldUpdated).not.toHaveBeenCalled();
  });

  it('shows the type deletion error without updating the stage', () => {
    const stage = { type: 'customStage', customField: 'old' } as IStage;
    const stageFieldUpdated = vi.fn();
    const { container } = render(<UnmatchedStageTypeStageConfig {...createProps(stage, stageFieldUpdated)} />);

    act(() => getEditor(container).setValue('{"customField":"new"}'));

    expect(screen.getByText('Error: Cannot delete property type.')).toBeVisible();
    expect(stage).toEqual({ type: 'customStage', customField: 'old' } as IStage);
    expect(stageFieldUpdated).not.toHaveBeenCalled();
  });
});
