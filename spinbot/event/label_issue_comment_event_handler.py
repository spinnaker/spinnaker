from gh import AddLabel, RemoveLabel
from .handler import Handler
from .command import GetCommands
from .issue_event import GetIssue

class LabelIssueCommentEventHandler(Handler):
    def __init__(self):
        super().__init__()

    def handles(self, event):
        return (event.type == 'IssueCommentEvent'
            and (event.payload.get('action') == 'created'
                or event.payload.get('action') == 'edited'))

    def handle(self, g, event):
        # avoid fetching until needed
        issue = None
        for command in GetCommands(event.payload.get('comment', {}).get('body')):
            if command[0] == 'add-label':
                if issue is None:
                    issue = GetIssue(g, event)
                for label in command[1:]:
                    AddLabel(g, issue, label, create=False)
            if command[0] == 'remove-label':
                if issue is None:
                    issue = GetIssue(g, event)
                for label in command[1:]:
                    RemoveLabel(g, issue, label, create=False)

LabelIssueCommentEventHandler()
