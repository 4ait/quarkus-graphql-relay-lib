package ru.code4a.graphql.relay.services.containers

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Container for node ID information that gets serialized into the global ID.
 *
 * @property nodeId The unique ID of the node type
 * @property entityId The database ID of the specific entity
 */
@Serializable
class GraphqlNodeEntityContainerID
@OptIn(ExperimentalSerializationApi::class)
constructor(
  @ProtoNumber(1)
  val nodeId: Long,
  @ProtoNumber(2)
  val entityId: Long
)
