package nu.metacraft.seedscan;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;

/** Runs a server command on the server thread from a given position and returns what it printed. */
final class Cmd {
    static List<String> run(MinecraftServer server, double x, double y, double z, String command) {
        List<String> output = new ArrayList<>();
        CommandSource capture = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                synchronized (output) {
                    output.add(message.getString());
                }
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        CompletableFuture<Void> done = new CompletableFuture<>();
        server.execute(() -> {
            try {
                CommandSourceStack source = server.createCommandSourceStack()
                    .withSource(capture)
                    .withLevel(server.overworld())
                    .withPosition(new Vec3(x, y, z));
                server.getCommands().performPrefixedCommand(source, command);
                done.complete(null);
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        done.join();
        synchronized (output) {
            return new ArrayList<>(output);
        }
    }
}
