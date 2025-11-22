package ru.code4a.graphql.relay.services

import jakarta.enterprise.context.ApplicationScoped
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import org.eclipse.microprofile.config.inject.ConfigProperty
import ru.code4a.graphql.relay.interfaces.GraphqlRelayDeterministicCipher
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*

/**
 * Service for encrypting and decrypting connection cursors.
 * Provides secure handling of pagination cursors to protect internal query state.
 *
 * @property cipher The encryption/decryption implementation
 */
@ApplicationScoped
class CursorCipherGraphqlNodeService(
  private val cipher: GraphqlRelayDeterministicCipher
) {
  /**
   * Encrypts a cursor container object into an opaque string cursor.
   *
   * @param cursorContainer The container object with cursor information
   * @return Encrypted string representation of the cursor
   */
  @OptIn(ExperimentalSerializationApi::class)
  fun encryptToCursor(cursorContainer: Any): String {
    val cursorData = ProtoBuf.encodeToByteArray(serializer(cursorContainer::class.java), cursorContainer)

    val encrypted = cipher.encrypt(cursorData)

    return Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted)
  }

  /**
   * Decrypts a cursor string into a typed object.
   *
   * @param clazz The expected class of the decrypted object
   * @param cursor The encrypted cursor string
   * @return Decrypted object of type T
   */
  @OptIn(ExperimentalSerializationApi::class)
  fun <T> decryptFromCursor(clazz: Class<T>, cursor: String): T {
    val cursorBytes = Base64.getUrlDecoder().decode(cursor)

    val decryptedCursor = cipher.decrypt(cursorBytes)

    return ProtoBuf.decodeFromByteArray(serializer(clazz), decryptedCursor) as T
  }
}
