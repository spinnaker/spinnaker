def AddArgs(parser):
    parser.add_argument(
            '--policy.enabled', 
            type=bool,
            default=True,
            help='Run the policy handlers.'
    )
