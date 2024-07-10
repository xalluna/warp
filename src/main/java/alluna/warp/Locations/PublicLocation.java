package alluna.warp.Locations;

import net.minecraft.server.command.ServerCommandSource;

public class PublicLocation extends Location {
    public String createdBy;

    public PublicLocation() {
        super();
    }

    public PublicLocation(ServerCommandSource source, String label) {
        super(source, label);

        this.createdBy = source.getName();
    }

    public boolean isOwnedBy(String name) {
        return this.createdBy.equals(name);
    }
}
