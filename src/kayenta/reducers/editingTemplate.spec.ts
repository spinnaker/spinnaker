// Using the parent 'selectedConfig' reducer because it has some of the
// logic for manipulating 'editingTemplate'.
import { selectedConfig as reducer } from './selectedConfig';
import {
  EDIT_TEMPLATE_BEGIN,
  EDIT_TEMPLATE_CANCEL,
  EDIT_TEMPLATE_CONFIRM,
  EDIT_TEMPLATE_NAME,
  EDIT_TEMPLATE_VALUE
} from 'kayenta/actions';

describe('Reducer: editingMetricReducer', () => {

  const createAction = (type: string, payload: any = {}) => ({
    type,
    payload,
  });

  it('handles editing template name & value', () => {
    const state: any = {
      config: {
        templates: {
          'my-template': 'my-value',
          'my-other-template': 'my-other-value',
        }
      }
    };

    const updatedState: any = [
      createAction(EDIT_TEMPLATE_BEGIN, {
        name: 'my-template',
        value: 'my-value'
      }),
      createAction(EDIT_TEMPLATE_NAME, {name: 'my-edited-template'}),
      createAction(EDIT_TEMPLATE_VALUE, {
        value: 'resource.metadata.tag.my-custom-tag-1=${tag1} ' +
               'AND resource.metadata.tag.my-custom-tag-2=${tag2}'
      }),
      createAction(EDIT_TEMPLATE_CONFIRM)
    ].reduce((s, action) => reducer(s, action), state);

    const {
      config: {
        templates,
      },
    } = updatedState;

    expect(templates).toEqual({
      'my-edited-template': 'resource.metadata.tag.my-custom-tag-1=${tag1} ' +
                            'AND resource.metadata.tag.my-custom-tag-2=${tag2}',
      'my-other-template': 'my-other-value',
    });
  });


  it('leaves template name & value unchanged on EDIT_TEMPLATE_CANCEL', () => {
    const state: any = {
      config: {
        templates: {
          'my-template': 'my-value',
          'my-other-template': 'my-other-value',
        }
      }
    };

    const updatedState: any = [
      createAction(EDIT_TEMPLATE_BEGIN, {
        name: 'my-template',
        value: 'my-value'
      }),
      createAction(EDIT_TEMPLATE_NAME, {name: 'my-edited-template'}),
      createAction(EDIT_TEMPLATE_VALUE, {
        value: 'resource.metadata.tag.my-custom-tag-1=${tag1} ' +
               'AND resource.metadata.tag.my-custom-tag-2=${tag2}'
      }),
      createAction(EDIT_TEMPLATE_CANCEL)
    ].reduce((s, action) => reducer(s, action), state);

    const {
      config: {
        templates,
      },
    } = updatedState;

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
});
