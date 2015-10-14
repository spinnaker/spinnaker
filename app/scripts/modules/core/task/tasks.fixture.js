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
        name: 'forceCacheRefresh',
      }
    ],
  },
  {
    id: 6,
    status: 'RUNNING',
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
        name: 'forceCacheRefresh',
      }
    ],
  },
  {
    id: 7,
    status: 'TERMINAL',
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
        name: 'forceCacheRefresh',
      }
    ],
  },
  {
    id: 8,
    status: 'SUSPENDED',
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
        name: 'forceCacheRefresh',
      }
    ],
  },
  {
    id: 9,
    status: 'SUCCEEDED',
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
        name: 'forceCacheRefresh',
      }
    ],
  }

];

TasksFixture.failedKatoTask = {
  id: 3,
  status: {
    completed: true,
    failed: true
  },
  history: [{
    phase: 'ORCHESTRATION',
    status: 'Orchestration failed.'
  }]
};

TasksFixture.successfulKatoTask = {
  id: 3,
  status: {
    completed: true,
    failed: false
  },
  history: [{
    phase: 'ORCHESTRATION',
    status: 'Orchestration completed.'
  }]
};

TasksFixture.runningKatoTask = {
  id: 3,
  status: {
    completed: false,
    failed: false
  },
  history: [{
    phase: 'ORCHESTRATION',
    status: 'Initializing Orchestration Task...'
  }]
};

module.exports = TasksFixture;
