package ru.code4a.graphql.relay.interfaces

/**
 * Interface for converting between entity and GraphQL node objects.
 * Implementations provide the logic for creating GraphQL node objects
 * from their corresponding entity objects.
 *
 * @param From The entity type
 * @param To The GraphQL node type
 */
interface GraphqlRelayResponseObjectBuilder<From, To> {
  /**
   * Converts an entity object to its corresponding GraphQL node object.
   *
   * @param from The entity object to convert
   * @return The corresponding GraphQL node object
   */
  fun get(from: From): To
}
