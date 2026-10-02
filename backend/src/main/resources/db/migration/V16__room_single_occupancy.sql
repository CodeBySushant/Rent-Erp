-- ============================================================
-- V16: one current tenant per room, enforced by the database
--
-- MembershipService.assignRoom already refuses a room that has an open
-- assignment (spec 10.2), but that is a read-then-insert: two requests at the
-- same moment could both pass the check. This partial unique index makes the
-- second insert fail instead, whatever the code path.
--
-- Safe on existing data: the service rule has always prevented two open
-- assignments on one room, and terminating a membership closes its assignments.
-- ============================================================

CREATE UNIQUE INDEX uq_room_assignments_room_open
    ON room_assignments (room_id)
    WHERE effective_to_bs IS NULL;
