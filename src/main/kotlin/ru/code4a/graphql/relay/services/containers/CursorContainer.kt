package ru.code4a.graphql.relay.services.containers

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Container for cursor information that gets serialized into the cursor string.
 *
 * @property nodeId The node type ID
 * @property cursorValues Values for each ordered field
 * @property cursorFields Names of the ordered fields
 */
@Serializable
class CursorContainer
@OptIn(ExperimentalSerializationApi::class)
constructor(
  @ProtoNumber(1)
  val nodeId: Long,
  @ProtoNumber(2)
  val cursorValues: List<String>,
  @ProtoNumber(3)
  val cursorFields: List<String>
)
