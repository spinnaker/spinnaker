import re
from .handler import Handler

class LogEventHandler(Handler):
    def __init__(self):
        super().__init__()

    def handles(self, event):
        return True

    def handle(self, g, event):
        company = event.actor.company
        if not company:
            company = "unknown"
        else:
            company = re.sub(r'\W+', '', company).lower()

        self.logging.info('{} ({}): @{} -> {}'.format(event.repo.name, event.created_at, event.actor.login, event.type))
        self.monitoring_db.write('event', { 'value': 1 }, tags={ 
            'repo': event.repo.name,
            'user': event.actor.login,
            'company': company,
            'type': event.type
        })
        if self.config.get('payload'):
            self.logging.info('  {}'.format(event.payload))

LogEventHandler()
