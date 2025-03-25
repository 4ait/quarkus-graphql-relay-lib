package ru.code4a.graphql.relay.schema.objects

import org.eclipse.microprofile.graphql.Id
import org.eclipse.microprofile.graphql.Name

/**
 * Core interface for all Relay-compatible GraphQL node objects.
 * Implements the Node interface from the Relay specification, providing a globally unique ID
 * that can be used to retrieve any object in the system.
 */
@Name("Node")
interface GraphqlNode {
  /**
   * The globally unique ID for this node.
   * This ID encodes both the entity type and its internal ID, allowing the system
   * to resolve any object from this single identifier.
   */
  @get:Id
  val id: String
}
