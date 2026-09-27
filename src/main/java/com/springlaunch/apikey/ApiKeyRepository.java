package com.springlaunch.apikey;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    /** Authentication path: the hash is unique, so this is a single index hit. */
    @Query("select k from ApiKey k join fetch k.organization where k.keyHash = :keyHash")
    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findByOrganizationIdOrderByIdDesc(Long organizationId);

    Optional<ApiKey> findByIdAndOrganizationId(Long id, Long organizationId);
}
