package dev.pvpbot.bot.movement;

public enum VerticalAction {
    NONE("NONE"),
    CRITICAL_SETUP("CRIT"),
    JUMP_RESET("JRESET"),
    INCOMING_KNOCKBACK("KB");

    private final String debugName;

    VerticalAction(String debugName) {
        this.debugName = debugName;
    }

    public String debugName() {
        return debugName;
    }
}
