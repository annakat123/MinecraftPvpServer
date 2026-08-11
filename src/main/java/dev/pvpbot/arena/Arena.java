package dev.pvpbot.arena;
import org.bukkit.Location;
public record Arena(int id, Location center, double halfSize) {
    public boolean contains(Location location) { return location.getWorld() == center.getWorld() && Math.abs(location.getX()-center.getX()) <= halfSize && Math.abs(location.getZ()-center.getZ()) <= halfSize && Math.abs(location.getY()-center.getY()) < 12; }
    public Location playerSpawn() { return center.clone().add(-6, 1, 0).setDirection(center.toVector().subtract(center.clone().add(-6,1,0).toVector())); }
    public Location botSpawn() { return center.clone().add(6, 1, 0).setDirection(center.toVector().subtract(center.clone().add(6,1,0).toVector())); }
}
