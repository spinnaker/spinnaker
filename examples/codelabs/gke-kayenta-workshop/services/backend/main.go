package main

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"strconv"
	"time"

	gce "cloud.google.com/go/compute/metadata"

	"golang.org/x/oauth2/google"

	"google.golang.org/api/monitoring/v3"
)

var count = 1
var lastTime = time.Now()

// Started with: https://github.com/GoogleCloudPlatform/golang-samples/blob/master/monitoring/custommetric/custommetric.go
func writeTimeSeriesValue(now string, s *monitoring.Service, metricType string, metricValue int64) error {
        // TODO(duftler): Check these errors.
	project_id, err := gce.ProjectID()
	location, err := gce.InstanceAttributeValue("cluster-location")
	cluster_name, err := gce.InstanceAttributeValue("cluster-name")
	timeseries := monitoring.TimeSeries{
		Metric: &monitoring.Metric{
			Type: metricType,
			Labels: map[string]string{
				"environment": "STAGING",
			},
		},
		Resource: &monitoring.MonitoredResource{
			Labels: map[string]string{
                                "project_id":     project_id,
                                "location":       location,
                                "cluster_name":   cluster_name,
                                "namespace_name": os.Getenv("NAMESPACE_NAME"),
                                "pod_name":       os.Getenv("POD_NAME"),
			},
			Type: "k8s_pod",
		},
		Points: []*monitoring.Point{
			{
				Interval: &monitoring.TimeInterval{
					StartTime: now,
					EndTime:   now,
				},
				Value: &monitoring.TypedValue{
                                      Int64Value: &metricValue,
				},
			},
		},
	}

	createTimeseriesRequest := monitoring.CreateTimeSeriesRequest{
		TimeSeries: []*monitoring.TimeSeries{&timeseries},
	}

	log.Printf("writeTimeseriesRequest: %s\n", formatResource(createTimeseriesRequest))
	_, err = s.Projects.TimeSeries.Create("projects/" + project_id, &createTimeseriesRequest).Do()
	if err != nil {
		return fmt.Errorf("Could not write time series value, %v ", err)
	}
	return nil
}

// formatResource marshals a response object as JSON.
func formatResource(resource interface{}) []byte {
	b, err := json.MarshalIndent(resource, "", "    ")
	if err != nil {
		panic(err)
	}
	return b
}

func index(w http.ResponseWriter, r *http.Request) {
	now := time.Now()
	fmt.Printf("** Elapsed: %v\n", now.Sub(lastTime).Seconds());
	if now.Sub(lastTime).Seconds() > 60 {
		fmt.Printf("** Going to record: %v\n", now);

		lastTime = now

		ctx := context.Background()
		// Move this up by count var and do it only once?
		s, err := createService(ctx)
		if err != nil {
			log.Fatal(err)
		}

		metricValue, err := strconv.ParseInt(os.Getenv("MY_APP_METRIC_VALUE"), 10, 64)
		if err != nil {
			log.Fatal(err)
		}

		if err := writeTimeSeriesValue(now.UTC().Format(time.RFC3339Nano), s, "custom.googleapis.com/my_app_metric", metricValue); err != nil {
			log.Fatal(err)
		}
	}

	fmt.Printf("Handling %+v\n", r);

	host, err := os.Hostname()

	if err != nil {
		http.Error(w, fmt.Sprintf("Error retrieving hostname: %v", err), 500)
		return
	}

	msg := fmt.Sprintf("Host: %s\nSuccessful requests: %d", host, count)
	count += 1

	io.WriteString(w, msg)
}

func createService(ctx context.Context) (*monitoring.Service, error) {
	hc, err := google.DefaultClient(ctx, monitoring.MonitoringScope)
	if err != nil {
		return nil, err
	}
	s, err := monitoring.New(hc)
	if err != nil {
		return nil, err
	}
	return s, nil
}

func main() {
	http.HandleFunc("/", index)
	port := ":8000"
	fmt.Printf("Starting to service on port %s\n", port);
	http.ListenAndServe(port, nil)
}
