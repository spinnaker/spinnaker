import readline
from getpass import getpass

class Completer(object):
    def __init__(self, options):
        self.options = options

    def complete(self, text, state):
        if state == 0:
            if text:
                self.matches = [c for c in self.options if c and c.startswith(text)]
            else:
                self.matches = self.options[:]

        try:
            return self.matches[state]
        except IndexError:
            return None

class Console:
    HEADER = '\033[95m'
    BLUE = '\033[34m'
    GREEN = '\033[32m'
    WARNING = '\033[33m'
    FAIL = '\033[31m'
    BOLD = '\033[1m'
    UNDERLINE = '\033[4m'
    ENDC = '\033[0m'

    @staticmethod
    def info(message):
        """Show an informational snippt."""
        print Console.BLUE + message + Console.ENDC

    @staticmethod
    def highlight(message):
        """Draw attention to this message."""
        print Console.GREEN + message + Console.ENDC

    @staticmethod
    def underline(message):
        """Draw attention to this message."""
        print Console.UNDERLINE + message + Console.ENDC

    @staticmethod
    def bold(message):
        """Draw attention to this message."""
        print Console.BOLD + message + Console.ENDC

    @staticmethod
    def warn(message):
        """Draw attention to a warning message."""
        print Console.WARNING + message + Console.ENDC

    @staticmethod
    def prompt_pass(message):
        """Get a password"""
        return getpass(Console.BOLD + message + Console.ENDC)

    @staticmethod
    def prompt(message, options=[], default=None, required=False, split=False):
        """Collect user input with the prompt message."""
        completer = Completer(options)
        readline.set_completer_delims(" \t\n;")
        readline.parse_and_bind("tab: complete")
        readline.set_completer(completer.complete)

        message = Console.BOLD + message + Console.ENDC
        if default:
            if isinstance(default, list):
                message = "Default:\n\t-{}\n{}".format("\n\t-".join(default), 
                        message)
            else:
                message = "{}[{}] ".format(message, default)

        if not required:
            message = "{}(optional) ".format(message)

        result = raw_input(message)
        if not result:
            result = default
        while not result and required:
            Console.warn("This input is required, try again...")
            result = raw_input(message)

        if split:
            if result is not None and not isinstance(result, list):
                result = filter(lambda x: x != '', result.split(" "))
                if len(result) == 0:
                    result = None

        return result

    @staticmethod
    def show(message):
        """Simply display this message."""
        print message

    @staticmethod
    def multiselect(options, prompt=None, custom=False, default=None):
        """Allow the user (with tab-completion, or index selection) to pick one
        of the listed options."""
        result = None
        while result is None:
            if prompt is not None:
                Console.bold(prompt)

            Console.show("Your options are: ")
            for i, option in enumerate(options):
                Console.show("\t[{}]: {}".format(i, option))
            if custom:
                Console.show("\t[_]: <custom>")

            selection = Console.prompt("Make a selection (index or name) ",
                    options=options, required=True, default=default)

            try:
                selection = int(selection)
                if selection < 0 or selection >= len(options):
                    Console.warn(
                            ("Invalid index: {}, must be in range "
                            + "[0, {}]").format(selection, len(options) - 1))
                else:
                    result = options[selection]
            except ValueError:
                if selection in options or custom:
                    result = selection
                else:
                    Console.show(("{} is not an index or a valid " 
                            + "option.").format(selection))

        return result

    @staticmethod
    def configure(op_class, op_func=None, options=None, 
            prompt=None, once=False):
        """Given some options and class with methods matching
        those option, keep allowing the user to select an action from the list
        of options, calling that action's class method until the user selects 
        "done"."""
        action = None
        while True:
            if options is None:
                actions = op_class.options
            else:
                actions = options

            action = Console.multiselect(actions + ["done"], prompt=prompt)
            if action == "done":
                return None
            else:
                if op_func is not None:
                    getattr(op_class, op_func)(action)
                else:
                    getattr(op_class, action)()
                if once:
                    return None
