package com.ultravyx.leaks.repo;

import com.ultravyx.leaks.domain.Lead;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadRepository extends JpaRepository<Lead, UUID> {
    List<Lead> findAllByOrganizationId(UUID organizationId);
    Optional<Lead> findByIdAndOrganizationId(UUID id, UUID organizationId);
    Optional<Lead> findByOrganizationIdAndExternalLeadId(UUID organizationId, String externalLeadId);
}
