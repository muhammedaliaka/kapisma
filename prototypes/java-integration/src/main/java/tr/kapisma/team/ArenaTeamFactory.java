package tr.kapisma.team;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import tr.kapisma.team.TeamService.GameTeam;

/** Uses the existing team service namespace; only called by the one active game. */
public final class ArenaTeamFactory {
    private ArenaTeamFactory(){}
    public static List<GameTeam> balanced(MinecraftServer server,List<ServerPlayer> red,List<ServerPlayer> blue){
        TeamService.clear(server);
        var sb=server.getScoreboard();
        var teams=new ArrayList<GameTeam>(2);
        for(int i=0;i<2;i++){
            String id=TeamService.PREFIX+"arena_"+i;
            var color=i==0?ChatFormatting.RED:ChatFormatting.AQUA;
            String name=i==0?"Kırmızı":"Mavi";
            GameTeam g=new GameTeam(i,id,color,name);
            PlayerTeam t=sb.addPlayerTeam(id);
            t.setColor(color);t.setDisplayName(Component.literal(name));t.setAllowFriendlyFire(false);
            for(var p:i==0?red:blue){sb.addPlayerToTeam(p.getScoreboardName(),t);g.members.add(p.getUUID());}
            teams.add(g);
        }
        return List.copyOf(teams);
    }
}
