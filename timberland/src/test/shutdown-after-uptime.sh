#!/usr/bin/env bash

UPTIME=$(cat /proc/uptime | awk -F ' ' '{print $1}')

if [ $(echo "$UPTIME > 1500" | bc -l) -ne 0 ]; then
  sudo poweroff
fi
