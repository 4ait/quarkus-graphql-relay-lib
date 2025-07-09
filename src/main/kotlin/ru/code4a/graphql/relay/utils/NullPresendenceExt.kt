package ru.code4a.graphql.relay.utils

import org.hibernate.query.NullPrecedence
import org.hibernate.query.Order


fun NullPrecedence.reverse(): NullPrecedence {
  return when (this) {
    NullPrecedence.NONE -> NullPrecedence.NONE
    NullPrecedence.FIRST -> NullPrecedence.LAST
    NullPrecedence.LAST -> NullPrecedence.FIRST
  }
}

fun <T> Order<T>.withNullPrecedence(nullPrecedence: NullPrecedence): Order<T> {
  return when (nullPrecedence) {
    NullPrecedence.NONE -> this
    NullPrecedence.FIRST -> withNullsFirst()
    NullPrecedence.LAST -> withNullsLast()
  }
}
