package com.careerradar.resume;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResumeRepository extends JpaRepository<Resume, Long> {

    Optional<Resume> findByPublicId(UUID publicId);

    List<Resume> findAllByOrderByUpdatedAtDesc();

    @Modifying(flushAutomatically = true)
    @Query("update Resume r set r.master = false where r.master = true")
    int clearMasterFlag();

    @Modifying(flushAutomatically = true)
    @Query("update Resume r set r.master = false where r.master = true and r.id <> :id")
    int clearOtherMasterFlags(@Param("id") Long id);
}
