package by.babanin.pipoker.service;

import java.util.UUID;

/**
 * Everyone has left the room, so it was deleted. Published by {@link RoomService}.
 */
public record RoomRemovedEvent(UUID roomId) {

}
