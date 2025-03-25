package ru.code4a.graphql.relay.annotations

/**
 * Annotation for GraphQL Relay node entity classes to associate them with a unique node ID.
 * This ID is used to identify the entity type in the global ID system.
 *
 * @param nodeId A unique identifier for this entity type. If not specified (MIN_VALUE),
 *               an ID will be generated based on the GraphQL type name.
 *               IMPORTANT: Once assigned and deployed to production, this ID should never change,
 *               as it would break client references to existing nodes.
 */
@Target(AnnotationTarget.CLASS)
annotation class GraphqlRelayNodeEntityById(
  val nodeId: Long = Long.MIN_VALUE,
)
