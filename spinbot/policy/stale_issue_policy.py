from gh import ObjectType, IssueRepo, HasLabel, AddLabel
from datetime import datetime
from .policy import Policy

class StaleIssuePolicy(Policy):
    def __init__(self):
        super().__init__()
        self.stale_days = self.config.get('stale_days')
        self.count = 0
        if not self.stale_days:
            self.stale_days = 120

    def applies(self, o):
        return ObjectType(o) == 'issue'

    def apply(self, g, o):
        now = datetime.now()
        delta = now - o.created_at
        # exit early to avoid listing all events
        if delta.days < self.stale_days:
            return

        if HasLabel(o, 'stale'):
            return

        repo = IssueRepo(o)

        newest_date = o.created_at
        for e in o.get_events():
            if e.created_at > newest_date:
                newest_date = e.created_at

        delta = now - newest_date
        if delta.days >= self.stale_days:
            self.logging.info("Tagging {} as stale after {} days since last activity".format(o.url, delta.days))
            AddLabel(g, o, 'stale')

StaleIssuePolicy()
