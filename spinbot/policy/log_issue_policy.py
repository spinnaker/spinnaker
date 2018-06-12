from gh import ObjectType, IssueRepo
from datetime import datetime
from .policy import Policy

class LogIssuePolicy(Policy):
    def __init__(self):
        super().__init__()

    def applies(self, o):
        return ObjectType(o) == 'issue'

    def apply(self, g, o):
        days_since_created = None
        if o.created_at is not None:
            now = datetime.now()
            days_since_created = (now - o.created_at).days

        repo = IssueRepo(o)

        self.monitoring_db.write('issue', { 
            'days_since_created': days_since_created,
            'count': 1
        }, tags={ 
            'repo': repo, 
            'user': o.user.login 
        })

LogIssuePolicy()
