from gh import ObjectType, IssueRepo
from datetime import datetime
from .policy import Policy

class LogIssuePolicy(Policy):
    def __init__(self):
        super().__init__()

    def applies(self, o):
        return ObjectType(o) == 'issue'

    def apply(self, g, o):
        now = datetime.now()
        delta = now - o.created_at
        repo = IssueRepo(o)
        self.monitoring_db.write('issue', { 
            'days_since_created': delta.days,
            'count': 1
        }, tags={ 
            'repo': repo, 
            'user': o.user.login 
        })

LogIssuePolicy()
