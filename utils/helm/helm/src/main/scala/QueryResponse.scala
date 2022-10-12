package com.radix.utils.helm

/**
 * Representative of a response from Consul / Nomad for an API call querying data, including
 * the values of the headers X-Consul-Index, X-Consul-KnownLeader, and X-Consul-LastContact
 * or X-Nomad-Index, X-Nomad-KnownLeader and X-Nomad-LastContact. X-Nomad-KnownLeader
 * and X-Nomad-LastContact (time in ms when the server was last contacted by the leader node)
 * is used to gauge the staleness of data received when a read query endpoint is queried
 * with the stale query parameter on requests (the default is strongly consistent read serviced
 * by the leader server. The stale mode allows any server to service the read, regardless of
 * whether it is the leader. This means reads can be arbitrarily stale; but results are generally
 * consistent to within 50 ms of the leader).
 * X-Consul-Index and X-Nomad-Index HTTP headers are returned for endpoints that support "blocking queries".
 * The Index value is a unique identifier that represents the current state of the requested resource. On
 * subsequent requests for this resource, the client can set the index query string parameter to the X-Consul-Index
 * or X-Nomad-Index value, indicating the client wishes to wait for any changes subsequent to that index. When this
 * is provided, the client will "hang" until a change in the system occurs, or the maximum timeout is reached. A
 * critical note is that the return of a blocking request is no guarantee of a change. It is possible that the timeout
 * was reached or that there was an idempotent write that does not affect the result of the query.
 * Note that the following APIs are known not to return these headers (as of Consul v1.0.6):
 *   - Anything under /agent/
 *   - /catalog/datacenters
 */
final case class QueryResponse[A](
  value: A,
  index: Long,
  knownLeader: Boolean,
  lastContact: Long,
)
