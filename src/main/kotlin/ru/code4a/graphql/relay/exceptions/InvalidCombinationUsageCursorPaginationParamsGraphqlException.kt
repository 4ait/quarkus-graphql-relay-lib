package ru.code4a.graphql.relay.exceptions

import org.eclipse.microprofile.graphql.GraphQLException

/**
 * Exception thrown when an invalid combination of cursor pagination parameters is used.
 * For example, using both 'first' and 'last' parameters in the same query.
 *
 * @param params List of parameter names that were invalidly combined
 * @param cause The root cause of this exception, if any
 */
class InvalidCombinationUsageCursorPaginationParamsGraphqlException(
  val params: List<String>,
  cause: Throwable? = null
) : GraphQLException(cause)
