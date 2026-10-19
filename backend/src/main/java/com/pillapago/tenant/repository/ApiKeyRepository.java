package com.pillapago.tenant.repository;

import com.pillapago.tenant.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio Spring Data JPA para la entidad ApiKey.
 * Incluye métodos para aislamiento multi-tenant estricto y resolución por hash.
 */
@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    /**
     * Lista todas las claves del comercio ordenadas descendentemente por fecha de creación.
     */
    List<ApiKey> findAllByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    /**
     * Obtiene una clave específica garantizando que pertenezca al comercio indicado.
     */
    Optional<ApiKey> findByIdAndMerchantId(UUID id, UUID merchantId);

    /**
     * Busca una clave activa a partir de su hash SHA-256 (para autenticación en el hot-path).
     */
    Optional<ApiKey> findByKeyHashAndActiveTrue(String keyHash);

    /**
     * Busca cualquier clave (activa o revocada) a partir de su hash SHA-256.
     */
    Optional<ApiKey> findByKeyHash(String keyHash);
}
