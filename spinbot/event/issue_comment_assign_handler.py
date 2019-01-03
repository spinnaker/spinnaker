from gh import AddLabel, RemoveLabel
from .handler import Handler
from .command import GetCommands
from .issue_event import GetIssue, GetRepo

class IssueCommentAssignHandler(Handler):
    def __init__(self):
        super().__init__()

    def handles(self, event):
        return (event.type == 'IssueCommentEvent'
            and (event.payload.get('action') == 'created'
                or event.payload.get('action') == 'edited'))

    def handle(self, g, event):
        # avoid fetching until needed
        issue = None
        def get_issue():
            nonlocal issue
            if issue is None:
                issue = GetIssue(g, event)
            if issue is None:
                raise RuntimeError('No issue found for {}'.format(event))
            return issue

        def get_user(command, issue):
            if len(command) == 1:
                issue.create_comment('You must specify someone to (un)assign, e.g. `@spinnakerbot (un)assign-issue user`')
                return

            user = command[1]
            if user[0] == '@':
                user = user[1:]

            return user

        for command in GetCommands(event.payload.get('comment', {}).get('body')):
            if command[0] == 'assign-issue':
                issue = get_issue()
                user = get_user(command, issue)
                repo = GetRepo(g, event) 
                if not repo.has_in_assignees(user):
                    issue.create_comment('User @{} cannot be assigned in this repo ({}). Are they in the correct org/team?'.format(user, repo.full_name))
                    return
                issue.add_to_assignees(user)

            if command[0] == 'unassign-issue':
                issue = get_issue()
                user = get_user(command, issue)
                has_assignee = len(list(filter(lambda a: a.login == user, issue.assignees))) > 0
                if not has_assignee:
                    issue.create_comment('User @{} is not assigned to this issue yet.'.format(user))
                    return
                issue.remove_from_assignees(user)

IssueCommentAssignHandler()
