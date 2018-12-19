from gh import ObjectType, IssueRepo, HasLabel, AddLabel
from datetime import datetime
from .policy import Policy

class StaleIssuePolicy(Policy):
    def __init__(self):
        super().__init__()
        self.stale_days = self.config.get('stale_days')
        self.ignore_lifecycle_label = self.config.get('ignore_lifecycle_label', 'no-lifecycle')
        self.count = 0
        if not self.stale_days:
            self.stale_days = 45

    def applies(self, o):
        return ObjectType(o) == 'issue'

    def apply(self, g, o):
        days_since_created = None
        days_since_updated = None
        now = datetime.now()
        if o.state == 'closed':
            return

        if o.created_at is not None:
            days_since_created = (now - o.created_at).days
        else:
            return

        if days_since_created < self.stale_days:
            return

        if o.updated_at is not None:
            days_since_updated = (now - o.updated_at).days
        else:
            return

        if days_since_updated < self.stale_days:
            return

        if HasLabel(o, self.ignore_lifecycle_label):
            return

        if HasLabel(o, 'to-be-closed'):
            o.create_comment("This issue is tagged as 'to-be-closed' and hasn't been updated " + 
                " in {} days, ".format(days_since_updated) + 
                "so we are closing it. You can always reopen this issue if needed.")
            o.edit(state='closed')
        elif HasLabel(o, 'stale'):
            o.create_comment("This issue is tagged as 'stale' and hasn't been updated " + 
                " in {} days, ".format(days_since_updated) + 
                "so we are tagging it as 'to-be-closed'. It will be closed " +
                "in {} days unless updates are made. ".format(self.stale_days) +
                "If you want to remove this label, comment:\n\n" +
                "> @spinnakerbot remove-label to-be-closed")
            AddLabel(g, o, 'to-be-closed')
        else:
            o.create_comment("This issue hasn't been updated in {} days, ".format(days_since_updated) + 
                "so we are tagging it as 'stale'. If you want to remove this label, comment:\n\n" +
                "> @spinnakerbot remove-label stale")
            AddLabel(g, o, 'stale')

StaleIssuePolicy()
