package dev.pvpbot.logic;
import dev.pvpbot.duel.match.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MatchStateMachineTest { @Test void acceptsLifecycle(){MatchStateMachine s=new MatchStateMachine();s.transition(MatchState.PREPARING);s.transition(MatchState.COUNTDOWN);s.transition(MatchState.ACTIVE);s.transition(MatchState.FINISHING);s.transition(MatchState.CLEANUP);s.transition(MatchState.FINISHED);assertEquals(MatchState.FINISHED,s.state());} @Test void rejectsInvalidTransition(){MatchStateMachine s=new MatchStateMachine();assertThrows(IllegalStateException.class,()->s.transition(MatchState.ACTIVE));} }
