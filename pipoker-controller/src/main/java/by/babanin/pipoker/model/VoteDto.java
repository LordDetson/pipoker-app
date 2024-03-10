package by.babanin.pipoker.model;

import by.babanin.pipoker.entity.Participant;
import jakarta.validation.constraints.NotNull;
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
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class VoteDto implements Comparable<VoteDto> {

    @NotNull
    private String nickname;

    @NotNull
    private String card;

    @EqualsAndHashCode.Include
    public String normalizeNickname() {
        return Participant.normalizeNickname(nickname);
    }

    @Override
    public int compareTo(VoteDto vote) {
        return Participant.NICKNAME_COMPARATOR.compare(nickname, vote.nickname);
    }
}
