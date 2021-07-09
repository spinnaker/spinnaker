import { RequestBuilder } from '../../api';
import { SpelService } from './SpelService';

describe('SpelService', () => {
  it('extracts "result" from the payload', async () => {
    const spy = spyOn(RequestBuilder.defaultHttpClient, 'get').and.callFake(() =>
      Promise.resolve({ result: 'data' } as any),
    );
    const result = await SpelService.evaluateExpression('expression', null, null);
    expect(spy).toHaveBeenCalledTimes(1);
    expect(result).toBe('data');
  });

  it('throws when the payload has "details"', async () => {
    const serverExpressionEvaluationFailure = {
      detail: {
        'bad expression': [
          {
            description:
              'Failed to evaluate [expression] EL1041E: After parsing a valid expression, ' +
              "there is still more data in the expression: 'expression'",
            exceptionType: 'org.springframework.expression.spel.SpelParseException',
            timestamp: 1572646924497,
            level: 'ERROR',
          },
        ],
      },
      result: '${bad expression}',
    };
    const errorDetail = serverExpressionEvaluationFailure.detail['bad expression'][0];

    // If expressions fail to evaluate, the server still returns 200 OK
    const spy = spyOn(RequestBuilder.defaultHttpClient, 'get').and.callFake(() => {
      return Promise.resolve(serverExpressionEvaluationFailure as any);
    });

    let rejection = null;
    try {
      await SpelService.evaluateExpression('expression', null, null);
    } catch (error) {
      rejection = error;
    }

    expect(spy).toHaveBeenCalledTimes(1);
    expect(rejection.message).toBe(`${errorDetail.exceptionType}: ${errorDetail.description}`);
  });
});
