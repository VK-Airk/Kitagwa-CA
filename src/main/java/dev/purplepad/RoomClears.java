package dev.purplepad;

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** Attributes the first observed checkmark on a connected room to the players inside it. */
final class RoomClears {
    private final BitSet completed = new BitSet(36);
    private boolean started;

    void reset() {
        completed.clear();
        started = false;
    }

    void update(byte[] map, int left, int top, int size, Map<String,Integer> positions,
                BiConsumer<Integer,List<String>> credit) {
        int stride = size + 4;
        int[] parents = new int[36];
        for (int i=0; i<36; i++) parents[i]=i;
        for (int i=0; i<36; i++) {
            int x=left+(i%6)*stride+size/2, z=top+(i/6)*stride+size/2;
            // A wide strip joins two tiles of the same room. A narrow doorway does not.
            if (i%6<5 && roomColor(pixel(map,x+stride/2,z-4))) join(parents,i,i+1);
            if (i/6<5 && roomColor(pixel(map,x-4,z+stride/2))) join(parents,i,i+6);
        }
        BitSet previouslyCleared = new BitSet(36), checked = new BitSet(36);
        for (int i=0; i<36; i++) {
            int x=left+(i%6)*stride, z=top+(i/6)*stride;
            int type=pixel(map,x,z), center=pixel(map,x+size/2,z+size/2);
            int group=root(parents,i);
            if (completed.get(i)) previouslyCleared.set(group);
            if (roomColor(type) && type!=30 && type!=82 && (center==30 || center==34))
                checked.set(group);
        }
        for (int i=0; i<36; i++) {
            int group=root(parents,i);
            if (checked.get(group) || previouslyCleared.get(group)) completed.set(i);
        }
        if (started) {
            checked.andNot(previouslyCleared);
            for (int group : checked.stream().toArray()) {
                var names=positions.entrySet().stream().filter(e->root(parents,e.getValue())==group)
                    .map(Map.Entry::getKey).toList();
                if (!names.isEmpty()) credit.accept(group,names);
            }
        }
        started=true;
    }

    private static boolean roomColor(int color) {
        return switch(color) { case 18,30,34,62,63,66,74,82,85 -> true; default -> false; };
    }
    private static int pixel(byte[] map,int x,int z) {
        return x>=0 && z>=0 && x<128 && z<128 ? map[x+128*z]&255 : 0;
    }
    private static int root(int[] parents,int i) {
        while (parents[i]!=i) i=parents[i];
        return i;
    }
    private static void join(int[] parents,int a,int b) {
        int x=root(parents,a), y=root(parents,b);
        parents[Math.max(x,y)]=Math.min(x,y);
    }
}
