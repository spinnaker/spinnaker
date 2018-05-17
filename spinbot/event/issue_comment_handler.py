from .handler import Handler

class IssueCommentHandler(Handler):
    def __init__(self):
        super().__init__()

    def handles(self, event):
        return event.type == 'IssueCommentEvent'

    def handle(self, g, event):
        self.logging.info(event.payload['comment']['body'])

IssueCommentHandler()
