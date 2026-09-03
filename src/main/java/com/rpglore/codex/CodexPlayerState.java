package com.rpglore.codex;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * A player's own Codex state: which catalog entries they hold and how. Common code
 * only. Sent on every mutation, whereas the catalog is only resent when it changes,
 * so this stays deliberately small — one short entry per collected book.
 *
 * @param catalogRevision the catalog revision this state was built against
 * @param entries         one entry per collected book; uncollected ids are absent
 */
public record CodexPlayerState(
        int catalogRevision,
        List<Entry> entries,
        boolean storeDuplicatesAsSpares,
        boolean allowCopy,
        boolean allowDuplicatePrevention,
        boolean revealUncollectedNames,
        boolean allowRunCommandClicks
) {

    public static final byte FLAG_READ = 1;
    public static final byte FLAG_FAVORITE = 2;

    /** Per-book state. {@code flags} packs READ and FAVORITE; {@code spares} is the extractable bank. */
    public record Entry(String id, byte flags, int spares, long discoveredAt) {

        public boolean read() {
            return (flags & FLAG_READ) != 0;
        }

        public boolean favorite() {
            return (flags & FLAG_FAVORITE) != 0;
        }

        public static byte packFlags(boolean read, boolean favorite) {
            return (byte) ((read ? FLAG_READ : 0) | (favorite ? FLAG_FAVORITE : 0));
        }

        void write(FriendlyByteBuf buf) {
            buf.writeUtf(id);
            buf.writeByte(flags);
            buf.writeVarInt(spares);
            buf.writeVarLong(discoveredAt);
        }

        static Entry read(FriendlyByteBuf buf) {
            return new Entry(buf.readUtf(), buf.readByte(), buf.readVarInt(), buf.readVarLong());
        }
    }

    public static CodexPlayerState empty() {
        return new CodexPlayerState(0, List.of(), true, false, false, false, false);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(catalogRevision);
        buf.writeCollection(entries, (b, e) -> e.write(b));
        buf.writeBoolean(storeDuplicatesAsSpares);
        buf.writeBoolean(allowCopy);
        buf.writeBoolean(allowDuplicatePrevention);
        buf.writeBoolean(revealUncollectedNames);
        buf.writeBoolean(allowRunCommandClicks);
    }

    public static CodexPlayerState read(FriendlyByteBuf buf) {
        int revision = buf.readVarInt();
        List<Entry> entries = buf.readCollection(ArrayList::new, Entry::read);
        boolean storeDuplicatesAsSpares = buf.readBoolean();
        boolean allowCopy = buf.readBoolean();
        boolean allowDuplicatePrevention = buf.readBoolean();
        boolean revealUncollectedNames = buf.readBoolean();
        boolean allowRunCommandClicks = buf.readBoolean();
        return new CodexPlayerState(revision, List.copyOf(entries), storeDuplicatesAsSpares,
                allowCopy, allowDuplicatePrevention, revealUncollectedNames, allowRunCommandClicks);
    }
}
