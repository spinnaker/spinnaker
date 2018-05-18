def AddArgs(parser):
    parser.add_argument(
            '--event.start_at', 
            help='Start the event handlers from this timestamp.'
    )

    parser.add_argument(
            '--event.enabled', 
            type=bool,
            default=True,
            help='Run the even handlers.'
    )
