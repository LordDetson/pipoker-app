package by.babanin.pipoker.model;

import by.babanin.pipoker.entity.Participant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ParticipantDto implements Comparable<ParticipantDto> {

    @NotBlank
    @Size(min = 2, max = 32)
    @ToString.Include
    private String nickname;

    private boolean watcher;

    @EqualsAndHashCode.Include
    public String normalizeNickname() {
        return Participant.normalizeNickname(nickname);
    }

    @Override
    public int compareTo(ParticipantDto participant) {
        return Participant.NICKNAME_COMPARATOR.compare(nickname, participant.nickname);
    }
}
