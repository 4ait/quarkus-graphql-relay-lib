package ru.code4a.graphql.relay.exceptions

import graphql.GraphQLException

/**
 * Exception thrown when a pagination limit exceeds the allowed maximum.
 *
 * @param limit The limit value that caused the exception
 * @param cause The root cause of this exception, if any
 */
class CannotBeTakenWithThisLimitGraphqlException(
  val limit: Long,
  cause: Throwable? = null
) : GraphQLException(cause)
