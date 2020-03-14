#!/usr/bin/env python3

import requests
import socket
import sys
import time

services_to_test = set([
  "apprise-apprise-apprise",
  "consul",
  "elasticsearch-elasticsearch-es-generic-node",
  "kafka-companion-daemons-kafkaCompanions-kSQL",
  "kafka-companion-daemons-kafkaCompanions-kafkaConnect",
  "kafka-companion-daemons-kafkaCompanions-kafkaRestProxy",
  "kafka-companion-daemons-kafkaCompanions-schemaRegistry",
  "kafka-daemons-kafka-kafka",
  "minio-minio-minio",
  "nomad",
  "nomad-client",
  "retool-retool-postgres",
  "retool-retool-retool-main",
  "yugabyte-yugabyte-ybmaster",
  "yugabyte-yugabyte-ybtserver",
  "zookeeper-daemons-zookeeper-zookeeper",
])

def wait_for_consul():
  try:
    socket.gethostbyname("consul.service.consul")
  except:
    print("Waiting for consul...")
    time.sleep(2)
    wait_for_consul()

wait_for_consul()

def get_service_set():
  return set(requests.get("http://consul.service.consul:8500/v1/catalog/services").json().keys())

registered_services = get_service_set()

while registered_services != services_to_test:
  waiting_for = services_to_test - registered_services
  print(f"Waiting for the following services: {waiting_for}")
  time.sleep(5)
  registered_services = get_service_set()

def get_service_check_health(service):
  checks = requests.get(f"http://consul.service.consul:8500/v1/health/checks/{service}").json()
  return [ { 'name': check['Name'], 'passing': check['Status'] == "passing" } for check in checks ]

def wait_for_all_checks(max_retries):
  results = [ check for service in registered_services for check in get_service_check_health(service) ]

  passing_checks = [ check['name'] for check in results if check['passing'] ]
  failing_checks = [ check['name'] for check in results if not check['passing'] ]

  if len(failing_checks) > 0:
    print(f"The following checks are passing: {passing_checks}")
    print(f"The following checks are failing: {failing_checks}")

    if max_retries > 0:
      time.sleep(5)
      wait_for_all_checks(max_retries - 1)
    else:
      print("Service check failed.")
      sys.exit(1)

wait_for_all_checks(45)

print("Service check succeeded.")
sys.exit(0)