package by.babanin.pipoker.dto;

import java.util.Objects;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@RequiredArgsConstructor
@ToString
public class Participant {

    @NotBlank
    @Size(min = 2, max = 32)
    private final String nickname;

    private final boolean watcher;

    @Override
    public boolean equals(Object o) {
        if(this == o) {
            return true;
        }
        if(o == null || getClass() != o.getClass()) {
            return false;
        }
        Participant that = (Participant) o;
        return nickname.equalsIgnoreCase(that.nickname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nickname);
    }
}
