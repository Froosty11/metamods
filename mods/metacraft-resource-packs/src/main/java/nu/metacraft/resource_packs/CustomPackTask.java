package nu.metacraft.resource_packs;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.network.ConfigurationTask;
import org.jspecify.annotations.NonNull;

import java.util.Set;
import java.util.function.Consumer;

public class CustomPackTask implements ConfigurationTask {

	public static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(ResourcePacks.getID("send_packs").toString());

	private final Set<ClientboundResourcePackPushPacket> packets;
	private int packsReceived = 0;

	public CustomPackTask(Set<ClientboundResourcePackPushPacket> packets) {
		this.packets = packets;
	}

	public boolean checkPacket(ServerboundResourcePackPacket packet) {
		if (packet.action().isTerminal()) {
			packsReceived++;
			if (packsReceived >= packets.size()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void start(@NonNull Consumer<Packet<?>> connection) {
		for (var pack : packets) {
			connection.accept(pack);
		}
	}

	@Override
	public @NonNull Type type() {
		return TYPE;
	}
}
