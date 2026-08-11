package dev.pvpbot.arena;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

public final class ArenaManager {
    private final List<Arena> arenas = new ArrayList<>(); private final Set<Integer> reserved = new HashSet<>();
    public ArenaManager(JavaPlugin plugin) {
        String name = plugin.getConfig().getString("arena.world", "pvpbot_arena"); int y = plugin.getConfig().getInt("arena.y",100), size = plugin.getConfig().getInt("arena.size",35), gap=plugin.getConfig().getInt("arena.gap",64), count=Math.max(1,plugin.getConfig().getInt("arena.count",4));
        World world = Bukkit.getWorld(name); if (world == null) world = new WorldCreator(name).type(WorldType.FLAT).generateStructures(false).createWorld();
        if (world == null) throw new IllegalStateException("Cannot create arena world " + name);
        world.setGameRule(GameRules.SPAWN_MOBS,false); world.setGameRule(GameRules.ADVANCE_WEATHER,false); world.setGameRule(GameRules.ADVANCE_TIME,false); world.setTime(6000); world.setDifficulty(Difficulty.NORMAL);
        for (int i=0;i<count;i++) { Location c=new Location(world,i*gap,y,0); generate(c,size); arenas.add(new Arena(i,c,size/2.0)); }
        plugin.getLogger().info("Arena world ready: " + name + ", arenas=" + arenas.size());
    }
    private void generate(Location c,int size) {
        int half=size/2, y=c.getBlockY(); World w=c.getWorld();
        for(int x=-half;x<=half;x++) for(int z=-half;z<=half;z++) { Block floor=w.getBlockAt(c.getBlockX()+x,y,c.getBlockZ()+z); floor.setType(Material.SMOOTH_STONE,false); for(int dy=1;dy<=5;dy++) w.getBlockAt(floor.getX(),y+dy,floor.getZ()).setType((Math.abs(x)==half||Math.abs(z)==half)?Material.GLASS:Material.AIR,false); }
    }
    public Optional<Arena> allocate() { return arenas.stream().filter(a->!reserved.contains(a.id())).findFirst().map(a->{reserved.add(a.id());return a;}); }
    public void release(Arena arena) { if (arena != null) reserved.remove(arena.id()); }
    public int reservedCount() { return reserved.size(); } public int totalCount() { return arenas.size(); }
    public void releaseAll() { reserved.clear(); }
}
