package by.babanin.pipoker.controller;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.modelmapper.ModelMapper;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import by.babanin.pipoker.PiPokerApplication;
import by.babanin.pipoker.activity.LeaveReason;
import by.babanin.pipoker.activity.RoomActivity;
import by.babanin.pipoker.entity.Deck;
import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Room;
import by.babanin.pipoker.entity.Round;
import by.babanin.pipoker.entity.Task;
import by.babanin.pipoker.entity.Timer;
import by.babanin.pipoker.entity.Vote;
import by.babanin.pipoker.event.ErrorEvent;
import by.babanin.pipoker.event.RoomEvent;
import by.babanin.pipoker.event.RoomEvent.EventType;
import by.babanin.pipoker.exception.ErrorCode;
import by.babanin.pipoker.exception.PiPokerException;
import by.babanin.pipoker.model.EstimateDto;
import by.babanin.pipoker.model.ParticipantDto;
import by.babanin.pipoker.model.RoomCreationDto;
import by.babanin.pipoker.model.RoomDto;
import by.babanin.pipoker.model.RoundDto;
import by.babanin.pipoker.model.TaskDto;
import by.babanin.pipoker.model.TimerDto;
import by.babanin.pipoker.model.VoteDto;
import by.babanin.pipoker.presence.RoomPresence;
import by.babanin.pipoker.presence.SeatLocks;
import by.babanin.pipoker.service.RoleChange;
import by.babanin.pipoker.service.RoomService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@Controller
@Validated
@MessageMapping(PiPokerApplication.ROOM_DESTINATION_PREFIX)
public class RoomController {

    private final RoomService roomService;
    private final RoomPresence roomPresence;
    private final SeatLocks seatLocks;
    private final RoomActivity activity;
    private final ModelMapper modelMapper;
    private final SimpMessageSendingOperations messagingTemplate;

    public RoomController(RoomService roomService, RoomPresence roomPresence, SeatLocks seatLocks, RoomActivity activity,
            ModelMapper modelMapper, SimpMessageSendingOperations messagingTemplate) {
        this.roomService = roomService;
        this.roomPresence = roomPresence;
        this.seatLocks = seatLocks;
        this.activity = activity;
        this.modelMapper = modelMapper;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/create")
    @SendToUser(destinations = PiPokerApplication.TOPIC_ROOM_CREATED_DESTINATION, broadcast = false)
    RoomDto create(@Valid RoomCreationDto roomCreationDto,
            @Header(SimpMessageHeaderAccessor.SESSION_ID_HEADER) String sessionId) {
        Deck deck = modelMapper.map(roomCreationDto.getDeck(), Deck.class);
        Set<Participant> participants = roomCreationDto.getParticipants().stream()
                .map(participantDto -> modelMapper.map(participantDto, Participant.class))
                .collect(Collectors.toUnmodifiableSet());
        Room room = roomService.create(roomCreationDto.getName(), deck, participants);
        activity.roomCreated();
        room.getParticipants().forEach(participant -> {
            roomPresence.hold(room.getId(), participant.getNickname(), sessionId);
            activity.joined(participant.isWatcher());
        });
        RoomDto result = modelMapper.map(room, RoomDto.class);
        modelMapper.validate();
        return result;
    }

    @SubscribeMapping("/{roomId}")
    RoomDto get(@DestinationVariable UUID roomId) {
        Room room = roomService.get(roomId);
        RoomDto result = modelMapper.map(room, RoomDto.class);
        modelMapper.validate();
        return result;
    }

    // Joining and leaving are told to the room while the person's seat lock is held (see SeatLocks)
    @MessageMapping({ "/{roomId}/participants/add", "/{roomId}/join" })
    void addParticipant(@DestinationVariable UUID roomId, @Valid ParticipantDto participantDto,
            @Header(SimpMessageHeaderAccessor.SESSION_ID_HEADER) String sessionId) {
        String nickname = participantDto.getNickname();
        seatLocks.change(roomId, nickname, () -> {
            Participant added = participantDto.isWatcher()
                    ? roomService.addWatcher(roomId, nickname)
                    : roomService.addParticipant(roomId, nickname);
            roomPresence.hold(roomId, added.getNickname(), sessionId);
            activity.joined(added.isWatcher());
            ParticipantDto result = modelMapper.map(added, ParticipantDto.class);
            modelMapper.validate();
            tellRoom(new RoomEvent(roomId, EventType.PARTICIPANT_ADDED, result));
        });
    }

    @MessageMapping({ "/{roomId}/participants/remove", "/{roomId}/participants/delete", "/{roomId}/left" })
    void removeParticipant(@DestinationVariable UUID roomId, @NotBlank String nickname) {
        seatLocks.change(roomId, nickname, () -> {
            ParticipantDto result = roomService.removeParticipant(roomId, nickname)
                    .map(participant -> {
                        activity.left(LeaveReason.LEFT);
                        return modelMapper.map(participant, ParticipantDto.class);
                    })
                    .orElse(null);
            roomPresence.forget(roomId, nickname);
            modelMapper.validate();
            tellRoom(new RoomEvent(roomId, EventType.PARTICIPANT_REMOVED, result));
        });
    }

    // Like joining, the change is told to the room while the person's seat lock is held
    @MessageMapping("/{roomId}/participants/role")
    void changeRole(@DestinationVariable UUID roomId, @Valid ParticipantDto participantDto) {
        String nickname = participantDto.getNickname();
        seatLocks.change(roomId, nickname, () -> {
            RoleChange change = roomService.changeRole(roomId, nickname, participantDto.isWatcher());
            activity.roleChanged(change.participant().isWatcher());
            if(change.takenBackVote() != null) {
                tellRoom(new RoomEvent(roomId, EventType.VOTE_REMOVED, modelMapper.map(change.takenBackVote(), VoteDto.class)));
            }
            ParticipantDto result = modelMapper.map(change.participant(), ParticipantDto.class);
            modelMapper.validate();
            tellRoom(new RoomEvent(roomId, EventType.PARTICIPANT_ROLE_CHANGED, result));
        });
    }

    // A browser that lost its connection or refreshed the page comes back to the seat it had, while it is kept
    @MessageMapping("/{roomId}/participants/return")
    @SendToUser(destinations = PiPokerApplication.TOPIC_ROOM_RETURNED_DESTINATION, broadcast = false)
    RoomEvent returnParticipant(@DestinationVariable UUID roomId, @NotBlank String nickname,
            @Header(SimpMessageHeaderAccessor.SESSION_ID_HEADER) String sessionId) {
        Participant participant = roomPresence.returnTo(roomId, nickname, sessionId);
        activity.returned();
        ParticipantDto result = modelMapper.map(participant, ParticipantDto.class);
        modelMapper.validate();
        return new RoomEvent(roomId, EventType.PARTICIPANT_RETURNED, result);
    }

    @MessageMapping({ "/{roomId}/votes/add", "/{roomId}/vote" })
    @SendTo(PiPokerApplication.TOPIC_ROOM_DESTINATION_PREFIX + ".{roomId}")
    RoomEvent addVote(@DestinationVariable UUID roomId, @Valid VoteDto vote) {
        Vote added = roomService.addVote(roomId, vote.getNickname(), vote.getCard());
        activity.voted();
        VoteDto result = modelMapper.map(added, VoteDto.class);
        modelMapper.validate();
        return new RoomEvent(roomId, EventType.VOTE_ADDED, result);
    }

    @MessageMapping({ "/{roomId}/votes/remove", "/{roomId}/votes/delete" })
    @SendTo(PiPokerApplication.TOPIC_ROOM_DESTINATION_PREFIX + ".{roomId}")
    RoomEvent removeVote(@DestinationVariable UUID roomId, @NotBlank String nickname) {
        VoteDto result = roomService.removeVote(roomId, nickname)
                .map(vote -> modelMapper.map(vote, VoteDto.class))
                .orElse(null);
        modelMapper.validate();
        return new RoomEvent(roomId, EventType.VOTE_REMOVED, result);
    }

    @MessageMapping("/{roomId}/votes/clear")
    @SendTo(PiPokerApplication.TOPIC_ROOM_DESTINATION_PREFIX + ".{roomId}")
    RoomEvent clearVotes(@DestinationVariable UUID roomId) {
        // First, so someone who comes back after refreshing the page meanwhile doesn't bring a vote into the new round:
        // either they come back without it, or their vote is cleared with the others
        roomPresence.votesCleared(roomId);
        Task task = roomService.clearVotes(roomId);
        activity.cleared();
        TaskDto result = task == null ? null : modelMapper.map(task, TaskDto.class);
        modelMapper.validate();
        return new RoomEvent(roomId, EventType.CLEAR_VOTES, result);
    }

    @MessageMapping("/{roomId}/votes/show")
    @SendTo(PiPokerApplication.TOPIC_ROOM_DESTINATION_PREFIX + ".{roomId}")
    RoomEvent showVotes(@DestinationVariable UUID roomId) {
        RoundDto round = roomService.showVotes(roomId)
                .map(recorded -> modelMapper.map(recorded, RoundDto.class))
                .orElse(null);
        activity.revealed();
        modelMapper.validate();
        return new RoomEvent(roomId, EventType.SHOW_VOTES, round);
    }

    private void tellRoom(RoomEvent event) {
        messagingTemplate.convertAndSend(PiPokerApplication.TOPIC_ROOM_DESTINATION_PREFIX + "." + event.getRoomId(), event);
    }

    @MessageMapping("/{roomId}/timer/start")
    @SendTo(PiPokerApplication.TOPIC_ROOM_DESTINATION_PREFIX + ".{roomId}")
    RoomEvent startTimer(@DestinationVariable UUID roomId, TimerDto timerDto) {
        Timer started = roomService.startTimer(roomId, Duration.ofSeconds(timerDto.getSeconds()));
        activity.timerStarted();
        TimerDto result = modelMapper.map(started, TimerDto.class);
        modelMapper.validate();
        return new RoomEvent(roomId, EventType.TIMER_STARTED, result);
    }

    @MessageMapping("/{roomId}/timer/stop")
    @SendTo(PiPokerApplication.TOPIC_ROOM_DESTINATION_PREFIX + ".{roomId}")
    RoomEvent stopTimer(@DestinationVariable UUID roomId) {
        roomService.stopTimer(roomId);
        return new RoomEvent(roomId, EventType.TIMER_STOPPED);
    }

    // Anyone in the room names what the round estimates while the cards are hidden; a blank name clears it
    @MessageMapping("/{roomId}/task")
    @SendTo(PiPokerApplication.TOPIC_ROOM_DESTINATION_PREFIX + ".{roomId}")
    RoomEvent setTask(@DestinationVariable UUID roomId, TaskDto taskDto) {
        Task task = roomService.setTask(roomId, taskDto.getName(), taskDto.getUrl());
        if(task != null) {
            activity.taskNamed();
        }
        TaskDto result = task == null ? null : modelMapper.map(task, TaskDto.class);
        modelMapper.validate();
        return new RoomEvent(roomId, EventType.TASK_CHANGED, result);
    }

    @MessageMapping("/{roomId}/estimate")
    @SendTo(PiPokerApplication.TOPIC_ROOM_DESTINATION_PREFIX + ".{roomId}")
    RoomEvent acceptEstimate(@DestinationVariable UUID roomId, @Valid EstimateDto estimateDto) {
        Round accepted = roomService.acceptEstimate(roomId, estimateDto.getRevealedAt(), estimateDto.getCard());
        activity.estimateAccepted();
        RoundDto result = modelMapper.map(accepted, RoundDto.class);
        modelMapper.validate();
        return new RoomEvent(roomId, EventType.ESTIMATE_ACCEPTED, result);
    }

    @MessageExceptionHandler
    @SendToUser(destinations = PiPokerApplication.TOPIC_ROOM_ERRORS_DESTINATION, broadcast = false)
    ErrorEvent handleException(Exception exception,
            @Header(name = SimpMessageHeaderAccessor.DESTINATION_HEADER, required = false) String destination) {
        return new ErrorEvent(destination, exception.getMessage(), errorCode(exception));
    }

    private static ErrorCode errorCode(Exception exception) {
        return switch(exception) {
            case PiPokerException refused -> refused.getCode();
            // The constraints of the messages and of the method parameters, checked before the method runs
            case MethodArgumentNotValidException _, ConstraintViolationException _ -> ErrorCode.INVALID_DATA;
            default -> ErrorCode.UNEXPECTED;
        };
    }
}
