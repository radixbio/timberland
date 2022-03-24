#!/usr/bin/env python3
import sys
import time
import urllib3
from typing import Dict, Set
import requests

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

services_to_test = {  # this whole list seems a little weird to me but what do i know
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
    #   "pgservice",
    #   "pgservice-sidecar-proxy",
    #   "retool-service",
    #   "retool-service-sidecar-proxy",
    "yb-masters-rpc-0",
    "yb-masters-rpc-0-sidecar-proxy",
    "yb-tserver-connect-0",
    "yb-tserver-connect-0-sidecar-proxy",
    "zookeeper-client-0",
    "zookeeper-client-0-sidecar-proxy",
    "zookeeper-follower-0",
    "zookeeper-follower-0-sidecar-proxy",
    "zookeeper-othersrvs-0",
    "zookeeper-othersrvs-0-sidecar-proxy",
}


def alt_print(*args: str) -> None:
    sys.stdout.write(" ".join(args) + "\n")
    sys.stdout.flush()


def consul(path: str) -> Dict:
    return requests.get(
        f"https://consul.service.consul:8501/v1/{path}",
        verify=False,
        cert=("/opt/radix/certs/cli/cert.pem", "/opt/radix/certs/cli/key.pem"),
    ).json()


def wait_for_services(max_retries: int, registered_services: Set[str]) -> None:
    waiting_for = services_to_test - registered_services
    if len(waiting_for) > 0:
        alt_print(f"Waiting for the following services: {waiting_for}")
        if max_retries == 0:
            alt_print("Failed to wait for services.")
            sys.exit(1)
        time.sleep(5)
        alt_print(f"{max_retries - 1} tries left.")
        wait_for_services(max_retries - 1, registered_services)
    else:
        alt_print("All services found. Now checking health check statuses.")


def wait_for_all_checks(max_retries: int, registered_services: Set[str]) -> None:
    failing_checks = [
        check["Name"]
        for service in registered_services
        for check in consul(f"health/checks/{service}")
        if check["Status"] != "passing"
    ]
    if len(failing_checks) > 0:
        alt_print(f"The following checks are failing: {failing_checks}")
        if max_retries == 0:
            alt_print("Service check failed.")
            sys.exit(1)
        time.sleep(5)
        alt_print(f"There are {max_retries - 1} tries left.")
        wait_for_all_checks(max_retries - 1, registered_services)
    else:
        alt_print("There are no failing checks.")


def main() -> None:
    services = set(consul("catalog/services").keys())
    wait_for_services(20, services)
    wait_for_all_checks(100, services)

    alt_print("Service check succeeded.")
    sys.exit(0)


if __name__ == "__main__":
    main()
