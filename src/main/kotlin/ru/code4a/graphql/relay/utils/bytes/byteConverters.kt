package ru.code4a.graphql.relay.utils.bytes

import java.nio.ByteBuffer

/**
 * Utility function to convert a ByteArray to a Long value.
 * Uses ByteBuffer to perform the conversion.
 *
 * @return The Long value represented by this ByteArray
 */
internal fun ByteArray.asLong(): Long {
  return ByteBuffer.wrap(this).getLong()
}
