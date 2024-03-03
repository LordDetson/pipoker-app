package by.babanin.pipoker.entity;

import java.util.Comparator;

import org.apache.commons.lang3.StringUtils;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class Participant implements Comparable<Participant> {

    public static final Comparator<String> NICKNAME_COMPARATOR = new NicknameComparator();

    @NotBlank
    @Size(min = 2, max = 32)
    @ToString.Include
    private final String nickname;

    private boolean watcher;

    public static Participant createParticipant(String nickname) {
        return new Participant(nickname, false);
    }

    public static Participant createWatcher(String nickname) {
        return new Participant(nickname, true);
    }

    private Participant(String nickname, boolean watcher) {
        this.nickname = StringUtils.trimToNull(nickname);
        this.watcher = watcher;
    }

    @Override
    public int compareTo(Participant participant) {
        return NICKNAME_COMPARATOR.compare(nickname, participant.nickname);
    }

    @EqualsAndHashCode.Include
    public String normalizeNickname() {
        return normalizeNickname(nickname);
    }

    public static String normalizeNickname(String nickname) {
        return StringUtils.toRootLowerCase(StringUtils.trimToNull(nickname));
    }

    private static final class NicknameComparator implements Comparator<String> {

        @Override
        public int compare(String nickname1, String nickname2) {
            return StringUtils.compare(
                    normalizeNickname(nickname1),
                    normalizeNickname(nickname2)
            );
        }
    }
}
