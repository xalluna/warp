package alluna.warp.Locations;

import net.minecraft.server.command.ServerCommandSource;

import java.time.OffsetDateTime;

public class PrivateLocation extends Location implements Cloneable {
	public PrivateLocation() {
		super();
	}

	public PrivateLocation(ServerCommandSource source, String label) {
		super(source, label);
	}

	@Override
	public PrivateLocation clone() {
		var location = new PrivateLocation();

		location.label = this.label;
		location.dimension = this.dimension;
		location.x = this.x;
		location.y = this.y;
		location.z = this.z;
		location.pitch = this.pitch;
		location.yaw = this.yaw;

//		location.createdDate = OffsetDateTime.now();

		return location;
	}
}
