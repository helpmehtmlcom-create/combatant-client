/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.map.locator;

import combatant.client.config.subsystem.MapTriangulationConfig;
import combatant.client.features.map.heuristic.HeuristicObservation;
import combatant.client.features.map.heuristic.HeuristicRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.waypoints.ClientWaypointManager;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class LocatorRuntime {
    private static final LocatorRuntime INSTANCE = new LocatorRuntime();
    private final AtomicReference<List<LocatorObservation>> published = new AtomicReference<>(List.of());
    private final Map<String, Revision> revisions = new HashMap<>();

    private LocatorRuntime() {}
    public static LocatorRuntime get() { return INSTANCE; }
    public List<LocatorObservation> snapshot() { return published.get(); }

    public void capture(Minecraft mc) {
        if(mc==null || mc.player==null || mc.level==null || mc.getConnection()==null) { clear(); return; }
        ClientWaypointManager manager=mc.getConnection().getWaypointManager();
        if(manager==null || !manager.hasWaypoints()) { clear(); return; }

        double ox=mc.player.getX(), oz=mc.player.getZ();
        long now=System.currentTimeMillis();
        List<LocatorObservation> out=new ArrayList<>();
        Map<String,Boolean> seen=new HashMap<>();
        Map<String,UUID> onlineByName=new HashMap<>();
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            if (info == null || info.getProfile() == null || info.getProfile().name() == null) continue;
            onlineByName.put(info.getProfile().name().toLowerCase(Locale.ROOT), info.getProfile().id());
        }

        manager.forEachWaypoint(mc.player,wp->{
            UUID uuid=wp.id().left().orElse(null);
            String name=wp.id().right().orElse("");
            if (uuid == null && !name.isBlank()) uuid = onlineByName.get(name.toLowerCase(Locale.ROOT));
            if (uuid == null && !name.isBlank() && MapTriangulationConfig.get().isTargetedName(name)) {
                uuid = MapTriangulationConfig.offlineTargetUuid(name);
            }
            if(uuid!=null && uuid.equals(mc.player.getUUID())) return;
            LocatorWaypointExtractor.Extracted e=LocatorWaypointExtractor.extract(wp);
            if(e.type()==LocatorObservationType.UNUSABLE)return;

            String key=uuid!=null?uuid.toString():"name:"+name.toLowerCase(Locale.ROOT);
            long fp=fingerprint(key,e,ox,oz);
            long rev=revision(key,fp);
            seen.put(key,Boolean.TRUE);

            LocatorObservation o=new LocatorObservation(uuid,name,e.type(),ox,oz,
                    e.x(),e.y(),e.z(),e.bearingRadians(),e.uncertaintyRadius(),now,rev);
            out.add(o);
            if(uuid!=null && e.type()==LocatorObservationType.BEARING_ONLY) {
                HeuristicRuntime.get().offer(new HeuristicObservation(uuid,name,ox,oz,e.bearingRadians(),now,rev,1.0));
            }
        });

        synchronized(revisions){ revisions.keySet().removeIf(k->!seen.containsKey(k)); }
        published.set(List.copyOf(out));
    }

    public void clear() {
        published.set(List.of());
        synchronized(revisions){ revisions.clear(); }
    }

    private long revision(String key,long fp) {
        synchronized(revisions) {
            Revision r=revisions.get(key);
            if(r==null){revisions.put(key,new Revision(fp,1));return 1;}
            if(r.fingerprint()!=fp){long n=r.revision()+1;revisions.put(key,new Revision(fp,n));return n;}
            return r.revision();
        }
    }

    private static long fingerprint(String key,LocatorWaypointExtractor.Extracted e,double observerX,double observerZ) {
        long h=0xcbf29ce484222325L;
        h=mix(h,key.hashCode()); h=mix(h,e.type().ordinal());
        h=mix(h,Double.doubleToLongBits(e.x())); h=mix(h,Double.doubleToLongBits(e.y()));
        h=mix(h,Double.doubleToLongBits(e.z())); h=mix(h,Double.doubleToLongBits(e.bearingRadians()));
        // Observer movement is part of a bearing observation. Quantize to blocks so standing still
        // does not manufacture revisions every tick, while movement can pass through the normal
        // baseline/information-gain gate instead of being rejected as a duplicate up front. Exact
        // and chunk-position sources must not change revision just because the observer moved.
        if (e.type()==LocatorObservationType.BEARING_ONLY) {
            h=mix(h,(long)Math.floor(observerX)); h=mix(h,(long)Math.floor(observerZ));
        }
        return h;
    }

    private static long mix(long h,long v){return (h^v)*0x100000001b3L;}
    private record Revision(long fingerprint,long revision){}
}
