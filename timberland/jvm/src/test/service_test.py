#!/usr/bin/env python3

import requests
import socket
import sys
import time
import urllib3

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)  # spams CI logs


def alt_print(*args):
    sys.stdout.write(" ".join(args)+"\n")
    sys.stdout.flush()


prefix = open("/opt/radix/timberland/release-name.txt", "r").read().strip()

services_to_test = set([service for service in [
#   "apprise",
#   "apprise-sidecar-proxy",
  "consul",
#   "es-rest-0",
#   "es-rest-0-sidecar-proxy",
#   "es-transport-0",
#   "es-transport-0-sidecar-proxy",
#   "ingress-service",
#   "kibana",
#   "kibana-sidecar-proxy",
  "kafka-0",
  "kafka-0-sidecar-proxy",
  "kc-schema-registry-service-0",
  "kc-schema-registry-service-0-sidecar-proxy",
  "kc-rest-proxy-service-0",
  "kc-rest-proxy-service-0-sidecar-proxy",
  "kc-connect-service-0",
  "kc-connect-service-0-sidecar-proxy",
  "kc-ksql-service-0",
  "kc-ksql-service-0-sidecar-proxy",
  "minio-local-service",
  "minio-local-service-sidecar-proxy",
  "nomad",
  "nomad-client",
#   "pgservice",
#   "pgservice-sidecar-proxy",
#   "retool-service",
#   "retool-service-sidecar-proxy",
  "vault",
  "yb-masters-rpc-0",
  "yb-masters-rpc-0-sidecar-proxy",
  "yb-tserver-connect-0",
  "yb-tserver-connect-0-sidecar-proxy",
  "zookeeper-client-0",
  "zookeeper-client-0-sidecar-proxy",
  "zookeeper-follower-0",
  "zookeeper-follower-0-sidecar-proxy",
  "zookeeper-othersrvs-0",
  "zookeeper-othersrvs-0-sidecar-proxy"
] if service not in "nomad nomad-client vault consul"])

def wait_for_consul():
  try:
    socket.gethostbyname("consul.service.consul")
  except:
    alt_print("Waiting for consul...")
    time.sleep(2)
    wait_for_consul()

# wait_for_consul()

def get_service_set():
  return set(requests.get("https://consul.service.consul:8501/v1/catalog/services", verify = False, cert=('/opt/radix/certs/cli/cert.pem', '/opt/radix/certs/cli/key.pem')).json().keys())

registered_services = get_service_set()

def wait_for_services(max_retries):
  registered_services = get_service_set()
  waiting_for = services_to_test - registered_services

  if len(waiting_for) > 0:
    alt_print(f"Waiting for the following services: {waiting_for}")

    if max_retries > 0:
      time.sleep(5)
      alt_print(f"There are {max_retries - 1} tries left.")
      wait_for_services(max_retries - 1)
    else:
      alt_print("Failed to wait for services.")
      sys.exit(1)
  else:
    alt_print("All services found. Now checking health check statuses.")

wait_for_services(20)

def get_service_check_health(service):
  checks = requests.get(f"https://consul.service.consul:8501/v1/health/checks/{service}", verify = False, cert=('/opt/radix/certs/cli/cert.pem', '/opt/radix/certs/cli/key.pem')).json()
  return [ { 'name': check['Name'], 'passing': check['Status'] == "passing" } for check in checks ]

# this is terrifyingly nondeterministic 
def wait_for_all_checks(max_retries):
  results = [ check for service in registered_services for check in get_service_check_health(service) ]

  passing_checks = [ check['name'] for check in results if check['passing'] ]
  failing_checks = [ check['name'] for check in results if not check['passing'] ]

  if len(failing_checks) > 0:
#     alt_print(f"The following checks are passing: {passing_checks}")
    alt_print(f"The following checks are failing: {failing_checks}")

    if max_retries > 0:
      time.sleep(5)
      alt_print(f"There are {max_retries - 1} tries left.")
      wait_for_all_checks(max_retries - 1)
    else:
      alt_print("Service check failed.")
      sys.exit(1)
  else:
    alt_print("There are no failing checks.")

wait_for_all_checks(100)

alt_print("Service check succeeded.")
sys.exit(0)
