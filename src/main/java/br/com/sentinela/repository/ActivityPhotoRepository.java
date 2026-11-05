package br.com.sentinela.repository;

import br.com.sentinela.entity.ActivityPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityPhotoRepository extends JpaRepository<ActivityPhoto, UUID> {
    List<ActivityPhoto> findAllByActivity_ActivityIdOrderByCreatedAtAsc(UUID activityId);
    Optional<ActivityPhoto> findByPhotoIdAndActivity_ActivityId(UUID photoId, UUID activityId);
    long countByActivity_ActivityId(UUID activityId);
}
