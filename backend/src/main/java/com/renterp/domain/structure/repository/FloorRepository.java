package com.renterp.domain.structure.repository;

import com.renterp.domain.structure.entity.Floor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FloorRepository extends JpaRepository<Floor, UUID> {

    Page<Floor> findByPropertyId(UUID propertyId, Pageable pageable);

    // Non-paginated — used internally by RoomService to resolve "every floor in this
    // property" before querying rooms across all of them (rooms only store floor_id,
    // never property_id — see Room entity comment).
    List<Floor> findAllByPropertyId(UUID propertyId);

    boolean existsByPropertyIdAndFloorNumber(UUID propertyId, short floorNumber);
}
