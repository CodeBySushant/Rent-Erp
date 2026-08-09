package com.renterp.domain.structure.repository;

import com.renterp.domain.structure.entity.Room;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {

    Page<Room> findByFloorId(UUID floorId, Pageable pageable);

    // Used by RoomService's ?propertyId= filter — floors resolved first via FloorRepository.
    Page<Room> findByFloorIdIn(List<UUID> floorIds, Pageable pageable);

    boolean existsByFloorIdAndName(UUID floorId, String name);

    // Used by FloorService.deleteFloor() to block deletion while active rooms remain.
    boolean existsByFloorIdAndActive(UUID floorId, boolean active);
}
