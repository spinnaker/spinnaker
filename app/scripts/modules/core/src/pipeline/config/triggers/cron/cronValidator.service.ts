import { API } from 'core/api/ApiService';

export class CronValidatorService {
  public static validate(expression: string) {
    const segments = expression ? expression.split(' ') : [];
    // ignore the last segment (year) if it's '*', since it just clutters up the description
    if (segments.length === 7 && segments[6] === '*') {
      segments.pop();
      expression = segments.join(' ');
    }
    return API.one('cron')
      .one('validate')
      .withParams({ expression: expression })
      .get();
  }
}
