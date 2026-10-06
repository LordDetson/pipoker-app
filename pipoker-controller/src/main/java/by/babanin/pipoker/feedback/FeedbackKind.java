package by.babanin.pipoker.feedback;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * What a person tells about PiPoker, as they chose it in the form.
 */
public enum FeedbackKind {

    // Something doesn't work as it should
    @JsonProperty("problem")
    PROBLEM,

    // Something PiPoker could do
    @JsonProperty("idea")
    IDEA,

    // What the person thinks of PiPoker
    @JsonProperty("review")
    REVIEW;

    // The same word as in JSON, for the issue's labels
    public String label() {
        return name().toLowerCase();
    }
}
