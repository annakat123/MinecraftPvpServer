package dev.pvpbot.duel.match;
import java.util.*;
public final class MatchStateMachine {
    private static final Map<MatchState, EnumSet<MatchState>> ALLOWED = new EnumMap<>(MatchState.class);
    static {
        ALLOWED.put(MatchState.CREATING, EnumSet.of(MatchState.PREPARING, MatchState.CLEANUP)); ALLOWED.put(MatchState.PREPARING, EnumSet.of(MatchState.COUNTDOWN, MatchState.CLEANUP));
        ALLOWED.put(MatchState.COUNTDOWN, EnumSet.of(MatchState.ACTIVE, MatchState.CLEANUP)); ALLOWED.put(MatchState.ACTIVE, EnumSet.of(MatchState.FINISHING, MatchState.CLEANUP));
        ALLOWED.put(MatchState.FINISHING, EnumSet.of(MatchState.CLEANUP)); ALLOWED.put(MatchState.CLEANUP, EnumSet.of(MatchState.FINISHED)); ALLOWED.put(MatchState.FINISHED, EnumSet.noneOf(MatchState.class));
    }
    private MatchState state = MatchState.CREATING;
    public MatchState state() { return state; }
    public void transition(MatchState next) { if (!ALLOWED.get(state).contains(next)) throw new IllegalStateException("Invalid match transition " + state + " -> " + next); state = next; }
}
