To runt the integration tests, the weave network must be running, attachable, and exposed (via `weave expose`).
Additionally, Timberland must be `runtime install`ed and Consul, Nomad, and Vault must all be running in the proper
configuration (which should be taken care of by `runtime install`.)
 
Nomad logs end up in `/tmp/nomad`, which you may have to clear out every once in a while.