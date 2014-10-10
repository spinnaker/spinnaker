var TasksFixture = {};
TasksFixture.initialSnapshot = [
  {
    id: 1,
    status: 'STARTED',
    variables: [
      {
        key: 'description',
        value: 'Resizing ASG',
      },
      {
        key: 'application',
        value: 'Hello world',
      }
    ],
    steps: [],
  },
  {
    id: 2,
    status: 'COMPLETED',
    variables: [
      {
        key: 'description',
        value: 'Resizing ASG',
      },
      {
        key: 'application',
        value: 'Hello world',
      }
    ],
    steps: [],
  },
  {
    id: 3,
    status: 'STARTED',
    variables: [
      {
        key: 'description',
        value: 'Resizing ASG',
      },
      {
        key: 'application',
        value: 'Hello world',
      }
    ],
    steps: [],
  },
];

TasksFixture.secondSnapshot = [
  {
    id: 1,
    status: 'COMPLETED',
    variables: [
      {
        key: 'description',
        value: 'Resizing ASG',
      },
      {
        key: 'application',
        value: 'Hello world',
      }
    ],
    steps: [],
  },
  {
    id: 2,
    status: 'COMPLETED',
    variables: [
      {
        key: 'description',
        value: 'Resizing ASG',
      },
      {
        key: 'application',
        value: 'Hello world',
      }
    ],
    steps: [],
  },
  {
    id: 3,
    status: 'STOPPED',
    variables: [
      {
        key: 'description',
        value: 'Resizing ASG',
      },
      {
        key: 'application',
        value: 'Hello world',
      }
    ],
    steps: [],
  },
  {
    id: 4,
    status: 'FAILED',
    variables: [
      {
        key: 'description',
        value: 'Resizing ASG',
      },
      {
        key: 'application',
        value: 'Hello world',
      }
    ],
    steps: [],
  },
  {
    id: 5,
    status: 'COMPLETED',
    variables: [
      {
        key: 'description',
        value: 'Resizing ASG',
      },
      {
        key: 'application',
        value: 'Hello world',
      }
    ],
    steps: [
      {
        name: 'ForceCacheRefreshStep',
      }
    ],
  }
];
