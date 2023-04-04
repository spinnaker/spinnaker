// Copyright (c) 2023 OpsMx, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.

package orca_tasks

import (
	"testing"
	"time"
)

func Test_calculateSleepTime(t *testing.T) {
	type args struct {
		now         int64
		lastTryTime int64
		attempts    int64
	}
	tests := []struct {
		name string
		args args
		want time.Duration
	}{
		{
			"attempt 1, lots of time remaining",
			args{100, 100000, 1},
			time.Duration(1) * time.Second,
		},
		{
			"attempt 2, lots of time remaining",
			args{100, 100000, 2},
			time.Duration(4) * time.Second,
		},
		{
			"attempt 100, lots of time remaining",
			args{100, 100000, 100},
			time.Duration(20) * time.Second,
		},
		{
			"attempt 10, 5 seconds remaining",
			args{0, 5, 10},
			time.Duration(6) * time.Second,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := calculateSleepTime(tt.args.now, tt.args.lastTryTime, tt.args.attempts); got != tt.want {
				t.Errorf("calculateSleepTime() = %v, want %v", got, tt.want)
			}
		})
	}
}
