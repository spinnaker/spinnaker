import { selectedConfig as reducer } from './selectedConfig';
import {
  DELETE_TEMPLATE,
  EDIT_TEMPLATE_BEGIN,
  EDIT_TEMPLATE_CANCEL,
  EDIT_TEMPLATE_CONFIRM,
  EDIT_TEMPLATE_NAME,
  EDIT_TEMPLATE_VALUE
} from 'kayenta/actions';

describe('Reducer: selectedConfig (templates)', () => {

  const createAction = (type: string, payload: any = {}) => ({
    type,
    payload,
  });

  const runActionSequence = (...actions: any[]) => (initialState: any) =>
      actions.reduce((s: any, a: any) => reducer(s, a), initialState);

  it('handles editing template name & value', () => {
    let state: any = {
      config: {
        templates: {
          'my-template': 'my-value',
          'my-other-template': 'my-other-value',
        }
      }
    };

    state = runActionSequence(
      createAction(EDIT_TEMPLATE_BEGIN, {
        name: 'my-template',
        value: 'my-value'
      }),
      createAction(EDIT_TEMPLATE_NAME, { name: 'my-edited-template' }),
      createAction(EDIT_TEMPLATE_VALUE, {
        value: 'resource.metadata.tag.my-custom-tag-1=${tag1} ' +
               'AND resource.metadata.tag.my-custom-tag-2=${tag2}'
      }),
      createAction(EDIT_TEMPLATE_CONFIRM),
    )(state);

    const {
      config: {
        templates,
      },
    } = state;

    expect(templates).toEqual({
      'my-edited-template': 'resource.metadata.tag.my-custom-tag-1=${tag1} ' +
                            'AND resource.metadata.tag.my-custom-tag-2=${tag2}',
      'my-other-template': 'my-other-value',
    });
  });


  it('leaves template name & value unchanged on EDIT_TEMPLATE_CANCEL', () => {
    let state: any = {
      config: {
        templates: {
          'my-template': 'my-value',
          'my-other-template': 'my-other-value',
        }
      }
    };

    state = runActionSequence(
      createAction(EDIT_TEMPLATE_BEGIN, {
        name: 'my-template',
        value: 'my-value'
      }),
      createAction(EDIT_TEMPLATE_NAME, { name: 'my-edited-template' }),
      createAction(EDIT_TEMPLATE_VALUE, {
        value: 'resource.metadata.tag.my-custom-tag-1=${tag1} ' +
               'AND resource.metadata.tag.my-custom-tag-2=${tag2}'
      }),
      createAction(EDIT_TEMPLATE_CANCEL),
    )(state);

    const {
      config: {
        templates,
      },
    } = state;

    expect(templates).toEqual({
      'my-template': 'my-value',
      'my-other-template': 'my-other-value',
    });
  });

  it('clears temporary variables on EDIT_TEMPLATE_CONFIRM', () => {
    const state: any = {
      editingTemplate: {
        name: 'my-template',
        editedName: 'my-edited-template',
        editedValue: '',
      },
      config: {
        templates: {
          'my-template': '',
        }
      }
    };

    const {
      editingTemplate: {
        name, editedName, editedValue,
      },
    } = reducer(state, createAction(EDIT_TEMPLATE_CONFIRM));

    [name, editedName, editedValue].forEach(
      value => expect(value).toEqual(null),
    );
  });

  it('deletes a template', () => {
    let state: any = {
      config: {
        templates: {
          'my-template': 'my-value',
          'my-other-template': 'my-other-value',
        }
      }
    };

    state = reducer(state, createAction(DELETE_TEMPLATE, { name: 'my-template' }));
    expect(Object.keys(state.config.templates)).toEqual(['my-other-template']);

    state = reducer(state, createAction(DELETE_TEMPLATE, { name: 'my-other-template' }));
    expect(Object.keys(state.config.templates)).toEqual([]);

    state = reducer(state, createAction(DELETE_TEMPLATE, { name: 'my-other-template' }));
    expect(Object.keys(state.config.templates)).toEqual([]);
  });

  it('adds a new template', () => {
    let state: any = {
      config: {
        templates: {
          'my-template': 'my-value',
        }
      }
    };

    state = runActionSequence(
      createAction(EDIT_TEMPLATE_BEGIN, { name: '', value: '' }),
      createAction(EDIT_TEMPLATE_VALUE, { value: 'new-value' }),
      createAction(EDIT_TEMPLATE_NAME, { name: 'new-name' }),
      createAction(EDIT_TEMPLATE_CONFIRM)
    )(state);

    expect(state.config.templates).toEqual({
      'my-template': 'my-value',
      'new-name': 'new-value',
    });
  });

  it('cleans up a newly added template on EDIT_TEMPLATE_CANCEL', () => {
    let state: any = {
      config: {
        templates: {
          'my-template': 'my-value',
        }
      }
    };

    state = runActionSequence(
      createAction(EDIT_TEMPLATE_BEGIN, { name: '', value: '' }),
      createAction(EDIT_TEMPLATE_VALUE, { value: 'new-value' }),
      createAction(EDIT_TEMPLATE_NAME, { name: 'new-name' }),
      createAction(EDIT_TEMPLATE_CANCEL)
    )(state);

    expect(state.config.templates).toEqual({
      'my-template': 'my-value',
    });
  });
});
