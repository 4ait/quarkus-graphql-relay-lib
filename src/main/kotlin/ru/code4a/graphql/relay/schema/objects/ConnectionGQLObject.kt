package ru.code4a.graphql.relay.schema.objects

import org.eclipse.microprofile.graphql.Name

/**
 * Generic GraphQL connection object that implements the Relay Connection specification.
 * Provides standardized pagination with cursors for any node type.
 *
 * @param T The type of node in the connection
 * @property edges List of edge objects containing nodes and their cursors
 * @property pageInfo Information about the connection's pagination state
 */
@Name("Connection")
data class ConnectionGQLObject<T>(
  val edges: List<Edge<T>>,
  val pageInfo: PageInfo
) {
  /**
   * Represents an edge in the connection, containing a node and its cursor.
   *
   * @param T The type of node
   * @property cursor A opaque string representing the position of this node in the connection
   * @property node The actual node data
   */
  data class Edge<T>(
    val cursor: String,
    val node: T
  )

  /**
   * Provides information about the connection's pagination state.
   *
   * @property hasPreviousPage Whether there are more items before the first edge
   * @property hasNextPage Whether there are more items after the last edge
   * @property startCursor The cursor of the first edge in the connection (lazy-loaded)
   * @property endCursor The cursor of the last edge in the connection (lazy-loaded)
   */
  data class PageInfo(
    val hasPreviousPage: Boolean,
    val hasNextPage: Boolean,
    private val startCursorBuilder: () -> String?,
    private val endCursorBuilder: () -> String?
  ) {
    /**
     * The cursor of the first edge in the connection.
     * Lazily evaluated to prevent unnecessary database queries.
     */
    val startCursor: String? by lazy {
      startCursorBuilder()
    }

    /**
     * The cursor of the last edge in the connection.
     * Lazily evaluated to prevent unnecessary database queries.
     */
    val endCursor: String? by lazy {
      endCursorBuilder()
    }
  }
}
