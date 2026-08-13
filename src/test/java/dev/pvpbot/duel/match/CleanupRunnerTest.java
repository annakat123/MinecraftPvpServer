package dev.pvpbot.duel.match;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CleanupRunnerTest {
    @Test void continuesAfterFailureAndReportsOperation() {
        List<String> completed=new ArrayList<>();
        List<String> errors=new ArrayList<>();
        CleanupRunner cleanup=new CleanupRunner((operation,error)->errors.add(operation+":"+error.getMessage()));

        cleanup.run("cancel task",()->completed.add("task"));
        cleanup.run("destroy NPC",()->{throw new IllegalStateException("Citizens failure");});
        cleanup.run("release arena",()->completed.add("arena"));
        cleanup.run("notify owner",()->completed.add("owner"));

        assertEquals(List.of("task","arena","owner"),completed);
        assertEquals(List.of("destroy NPC:Citizens failure"),errors);
    }
}
