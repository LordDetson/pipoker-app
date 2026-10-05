package by.babanin.pipoker.service;

import by.babanin.pipoker.entity.Participant;
import by.babanin.pipoker.entity.Vote;

/**
 * A participant who became a watcher or a voter.
 *
 * @param participant the participant with the new role
 * @param takenBackVote the vote of this round that went when they became a watcher, null when there was none
 */
public record RoleChange(Participant participant, Vote takenBackVote) {
}
