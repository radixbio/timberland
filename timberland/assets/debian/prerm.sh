if [ -f /opt/radix/timberland/exec/timberland ]; then
    cd /opt/radix/timberland/exec/
    ./timberland runtime dns down
fi

rm -rf /opt/radix/terraform/
