package by.babanin.pipoker.dto;

import java.util.HashMap;
import java.util.Map;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VotingResult {

    @Size(max = 20)
    private final Map<String, Card> map = new HashMap<>();
}
